import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Main {
  private final static int PORT = 4221;
  private final static int THREAD_POOL_SIZE = 10;

  private final static String RESPONSE_VERSION = "HTTP/1.1 ";
  private final static String SUCCESS = "200 OK";
  private final static String NOT_FOUND = "404 Not Found";

  private final static String USER_AGENT = "user-agent";

  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");

    try (ServerSocket serverSocket = new ServerSocket(PORT); ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
      // Since the tester restarts your program quite often, setting SO_REUSEADDR
      // ensures that we don't run into 'Address already in use' errors
      serverSocket.setReuseAddress(true);

      while (true) {
        Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
        System.out.println("Accepted incoming socket connection: " + clientSocket.getRemoteSocketAddress());

        executor.submit(() -> handleIncomingConnection(clientSocket));
      }

    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  public static void handleIncomingConnection(Socket clientSocket) {
    try (BufferedReader socketReadBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); PrintWriter socketWriter = new PrintWriter(clientSocket.getOutputStream(), true)) {
      String requestLine = socketReadBuffer.readLine();
      if (requestLine == null) {
        System.out.println("Received preflight / handshake request. Closing socket " + clientSocket.getRemoteSocketAddress());
        clientSocket.close();
        return;
      }
      String[] requestHTTPTokens = requestLine.split(" ");
      Map<String, String> headers = new HashMap<>();
      String nextLine;
      while((nextLine = socketReadBuffer.readLine()) != null) {
        if (nextLine.isEmpty()) {
          break;
        }
        final int headerSplitIndex = nextLine.indexOf(": ");
        final String headerKey = nextLine.substring(0, headerSplitIndex).toLowerCase();
        final String headerValue = nextLine.substring(headerSplitIndex + 2);
        headers.put(headerKey, headerValue);
      }
      System.out.println("Request received: " + Arrays.toString(requestHTTPTokens) + "\n" + headers);

      if (requestHTTPTokens.length == 3) {
        final String path = requestHTTPTokens[1];
        if (path.equals("/")) {
          socketWriter.println(RESPONSE_VERSION + SUCCESS + "\r\n\r\n");
        } else if (path.startsWith("/echo")) {
          socketWriter.println(handleEchoPath(path));
        } else if (path.equals("/user-agent")) {
          socketWriter.println(returnHeader(headers, USER_AGENT));
        } else {
          socketWriter.println(RESPONSE_VERSION + NOT_FOUND + "\r\n\r\n");
        }
        clientSocket.close();
      } else {
        throw new IOException("Invalid HTTP Request Format");
      }
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }

  public static StringBuilder handleEchoPath(final String path) throws IOException {
    final String[] echoResp = path.split("/echo/");
    if (echoResp.length == 2) {
      String[] headers = {"Content-Type: text/plain", "Content-Length: " + echoResp[1].length()};
      StringBuilder respBuilder = new StringBuilder();
      respBuilder.append(RESPONSE_VERSION).append(SUCCESS).append("\r\n");
      for (final String header : headers) {
        respBuilder.append(header).append("\r\n");
      }
      return respBuilder.append("\r\n").append(echoResp[1]);
    }
    throw new IOException("Echo path is malformed");
  }

  public static StringBuilder returnHeader(final Map<String, String> headerMap, final String respHeader) throws IOException {
    if (headerMap.get(respHeader) != null) {
      String[] headers = {"Content-Type: text/plain", "Content-Length: " + headerMap.get(respHeader).length()};
      StringBuilder respBuilder = new StringBuilder();
      respBuilder.append(RESPONSE_VERSION).append(SUCCESS).append("\r\n");
      for (final String header : headers) {
        respBuilder.append(header).append("\r\n");
      }
      respBuilder.append("\r\n").append(headerMap.get(respHeader));
      return respBuilder;
    }
    throw new IOException("User Agent Path request received without user-agent header");
  }
}
