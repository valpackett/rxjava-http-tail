package rx;

import rx.Observable;
import rx.util.functions.Func1;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.util.ExponentialBackOff;

public class HttpTail {

  public static final class TailResult {
    private final String body;
    private final long offset;

    public String getBody() { return body; }
    public long getOffset() { return offset; }

    public TailResult(String aBody, long anOffset) {
      body = aBody;
      offset = anOffset;
    }
  }

  private final GenericUrl url;
  private final HttpRequestFactory requestFactory;
  private final long startingOffset;
  private final long delay;

  public HttpTail(String aUrl, long aStartingOffset, long aDelay) {
    url = new GenericUrl(aUrl);
    HttpTransport transport = new ApacheHttpTransport();
    requestFactory = transport.createRequestFactory();
    startingOffset = aStartingOffset;
    delay = aDelay;
  }

  public Observable createObservable() {
    return Observable.create(new Func1<Observer<TailResult>, Subscription>() {
      public Subscription call(Observer<TailResult> observer) {
        final Observer fObserver = observer;

        final Thread t = new Thread(new Runnable() {
          private long offset;

          public void run() {
            offset = startingOffset;

            new Timer().schedule(new TimerTask() {
              long getLength() throws IOException {
                HttpRequest request = requestFactory.buildHeadRequest(url);
                request.setUnsuccessfulResponseHandler(new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff()));
                HttpResponse response = request.execute();
                return response.getHeaders().getContentLength();
              }

              HttpResponse getContent(long currentLength) throws IOException {
                HttpRequest request = requestFactory.buildGetRequest(url);
                request.setUnsuccessfulResponseHandler(new HttpBackOffUnsuccessfulResponseHandler(new ExponentialBackOff()));
                HttpHeaders requestHeaders = new HttpHeaders();
                requestHeaders.setRange(String.format("bytes=%d-%d", offset, currentLength));
                request.setHeaders(requestHeaders);
                return request.execute();
              }

              public void run() {
                try {
                  long currentLength = getLength();
                  if (currentLength != offset) {
                    HttpResponse response = getContent(currentLength);
                    offset += response.getHeaders().getContentLength();
                    fObserver.onNext(new TailResult(response.parseAsString(), offset));
                  }
                } catch (IOException e) { }
              }
            }, 0, delay);
          }
        });
        t.start();

        return new Subscription() {
          public void unsubscribe() {
            t.interrupt();
          }
        };
      }
    });
  }
}
