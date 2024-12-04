import Models.ResponseBody;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static Utils.Constants.*;
import static Utils.Helper.getBasePath;
import static Utils.Helper.getHeaderString;

public final class RequestHandler {
  private static String[] serverArgs;

  public RequestHandler(String[] args) {
    serverArgs = args;
  }

  public void handleIncomingConnection(Socket clientSocket) {
    try (BufferedReader socketReadBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
         OutputStream socketOutputStream = clientSocket.getOutputStream();
         PrintWriter socketWriter = new PrintWriter(socketOutputStream, true)) {
      String requestLine = socketReadBuffer.readLine();
      if (requestLine == null) {
        System.out.println("Received preflight / handshake request. Closing socket " + clientSocket.getRemoteSocketAddress());
        clientSocket.close();
        return;
      }
      String[] requestHTTPTokens = requestLine.split(" ");
      Map<String, String> headers = new HashMap<>();
      String nextLine;
      while ((nextLine = socketReadBuffer.readLine()) != null) {
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
        String reqBody = null;
        if (requestHTTPTokens[0].equals(POST)) {
          if (headers.get(CONTENT_LENGTH) == null) {
            throw new IOException("POST requests must send a content-length header");
          }
          int reqBodySize = Integer.parseInt(headers.get(CONTENT_LENGTH));
          char[] reqBodyBuffer = new char[reqBodySize];
          socketReadBuffer.read(reqBodyBuffer, 0, reqBodySize);
          reqBody = new String(reqBodyBuffer);
        }
        final String path = requestHTTPTokens[1];
        if (path.equals("/")) {
          socketWriter.println(RESPONSE_VERSION + SUCCESS + "\r\n\r\n");
        } else if (path.equals("/user-agent")) {
          sendResponse(headers, reqBody, returnHeader(headers, USER_AGENT), socketOutputStream);
        } else if (path.startsWith("/echo/")) {
          sendResponse(headers, reqBody, handleEchoPath(path), clientSocket.getOutputStream());
        } else if (path.startsWith("/files/")) {
          if (requestHTTPTokens[0].equals(GET)) {
            sendResponse(headers, reqBody, returnFileContent(path), socketOutputStream);
          } else if (requestHTTPTokens[0].equals(POST)) {
            sendResponse(headers, reqBody, createFile(path, reqBody), socketOutputStream);
          }
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

  public static ResponseBody handleEchoPath(final String path) throws IOException {
    final String echoResp = path.substring(6);
    Map<String, String> headers = new HashMap<>();
    headers.put(CONTENT_TYPE, "text/plain");
    return new ResponseBody(RESPONSE_VERSION, SUCCESS, headers, echoResp.getBytes());
  }

  public static ResponseBody returnHeader(final Map<String, String> headerMap, final String respHeader) throws IOException {
    if (headerMap.get(respHeader) != null) {
      Map<String, String> headers = new HashMap<>();
      headers.put(CONTENT_TYPE, "text/plain");
      return new ResponseBody(RESPONSE_VERSION, SUCCESS, headers, headerMap.get(respHeader).getBytes());
    }
    throw new IOException("User Agent Path request received without user-agent header");
  }

  public static ResponseBody returnFileContent(final String path) throws IOException {
    final String filename = path.substring(7);
    if (!filename.isEmpty()) {
      File file = new File(getBasePath(serverArgs), filename);
      if (file.exists()) {
        byte[] fileData = Files.readAllBytes(file.toPath());
        Map<String, String> headers = new HashMap<>();
        headers.put(CONTENT_TYPE, "application/octet-stream");
        return new ResponseBody(RESPONSE_VERSION, SUCCESS, headers, new String(fileData).getBytes());
      } else {
        return new ResponseBody(RESPONSE_VERSION, NOT_FOUND, null, "".getBytes());
      }
    }
    throw new IOException("File path is malformed");
  }

  public static ResponseBody createFile(final String path, final String reqBody) throws IOException {
    final String filename = path.substring(7);
    if (!filename.isEmpty()) {
      File file = new File(getBasePath(serverArgs), filename);
      if (!file.exists()) {
        Files.createFile(Path.of(getBasePath(serverArgs) + filename));
      }
      Files.writeString(file.toPath(), reqBody);
      return new ResponseBody(RESPONSE_VERSION, CREATED, null, "".getBytes());
    }
    throw new IOException("File path is malformed");
  }

  private static void sendResponse(Map<String, String> reqHeaderMap, String reqBody, ResponseBody response, OutputStream socketWriter) throws IOException {
    final String acceptEncoding = reqHeaderMap.get(ACCEPT_ENCODING);
    if (response.respHeaders == null) {
      response.respHeaders = new HashMap<>();
    }
    if (acceptEncoding != null) {
      String[] encodings = acceptEncoding.split(", ");
      final List<String> supportedEncodings = List.of(SUPPORTED_ENCODING);
      // OR: Stream.of(SUPPORTED_ENCODING).anyMatch(encoding::equals)
      String respEncoding = Arrays.stream(encodings).filter(supportedEncodings::contains).findFirst().orElse(null);
      if (respEncoding != null) {
        response.respHeaders.put(CONTENT_ENCODING, respEncoding);
        if (respEncoding.equalsIgnoreCase("gzip")) {
          try (var responseBuffer = new ByteArrayOutputStream();
               var compressor = new GZIPOutputStream(responseBuffer)) {
            System.out.println("Response size before compression: " + response.respBody.length);
            compressor.write(response.respBody);
            compressor.finish();
            response.respBody = responseBuffer.toByteArray();
            System.out.println("Response size after compression: " + response.respBody.length);
          }
        }
      }
    }
    response.respHeaders.put(CONTENT_LENGTH, String.valueOf(response.respBody.length));
    System.out.println(response.httpVersion + response.respStatus + "\r\n" + getHeaderString(response.respHeaders) + "\r\n" + Arrays.toString(response.respBody));
    socketWriter.write((response.httpVersion + response.respStatus + "\r\n" + getHeaderString(response.respHeaders) + "\r\n").getBytes());
    socketWriter.write(response.respBody);
  }
}
