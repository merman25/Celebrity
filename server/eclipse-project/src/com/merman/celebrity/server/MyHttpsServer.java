package com.merman.celebrity.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.merman.celebrity.server.handlers.AHttpHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

/*
 * Adapted from https://stackoverflow.com/questions/2308479/simple-java-https-server
 */
public class MyHttpsServer {
	static int port = 8443;
	
	private HttpsServer httpsServer;
	
	public MyHttpsServer() {
		try {
		    // Set up the socket address
		    InetSocketAddress address = new InetSocketAddress(InetAddress.getLocalHost(), port);

		    // Initialise the HTTPS server
		    httpsServer = HttpsServer.create(address, 10);
		    SSLContext sslContext = SSLContext.getInstance("TLS");

		    // Initialise the keystore
		    char[] password = "simulator".toCharArray();
		    KeyStore ks = KeyStore.getInstance("JKS");
		    
		    /* To generate a keystore:
		     * keytool -genkeypair -keyalg RSA -alias self_signed -keypass simulator -keystore lig.keystore -storepass simulator
		     */
		    FileInputStream fis = new FileInputStream("lig.keystore");
		    ks.load(fis, password);

		    // Set up the key manager factory
		    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		    kmf.init(ks, password);

		    // Set up the trust manager factory
		    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		    tmf.init(ks);

		    // Set up the HTTPS context and parameters
		    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		    httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
		        @Override
				public void configure(HttpsParameters params) {
		            try {
		                // Initialise the SSL context
		                SSLContext c = SSLContext.getDefault();
		                SSLEngine engine = c.createSSLEngine();
		                params.setNeedClientAuth(false);
		                params.setCipherSuites(engine.getEnabledCipherSuites());
		                params.setProtocols(engine.getEnabledProtocols());

		                // Get the default parameters
		                SSLParameters defaultSSLParameters = c.getDefaultSSLParameters();
		                params.setSSLParameters(defaultSSLParameters);
		            } catch (Exception ex) {
		                ex.printStackTrace();;
		                System.err.println("Failed to create HTTPS port");
		            }
		        }
		    });
//		    LigServer server = new LigServer(httpsServer);
//		    joinableThreadList.add(server.getJoinableThread());
		    
		    httpsServer.createContext("/tryit", new AHttpHandler() {
				
				@Override
				public void _handle(HttpExchange aExchange) throws IOException {
					System.out.println("in secure handler");
//					dumpRequest(aExchange);
					
					boolean success = false;
					try {
						Headers requestHeaders = aExchange.getRequestHeaders();
						for ( Entry<String, List<String>> l_mapEntry : requestHeaders.entrySet() ) {
							if ( l_mapEntry.getKey().equals("Sec-websocket-key") ) {
								String key = l_mapEntry.getValue().get(0);
								byte[] response;
								String encodedString = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")));
								response = ("HTTP/1.1 101 Switching Protocols\r\n"
										+ "Connection: Upgrade\r\n"
										+ "Upgrade: websocket\r\n"
										+ "Sec-WebSocket-Accept: "
										+ encodedString
										+ "\r\n\r\n").getBytes("UTF-8");
								
								aExchange.getResponseHeaders().set("Connection", "Upgrade");
								aExchange.getResponseHeaders().set("Upgrade", "websocket");
								aExchange.getResponseHeaders().set("Sec-WebSocket-Accept", encodedString);
								
								aExchange.sendResponseHeaders(HTTPResponseConstants.Switching_Protocols, -1);
//								aExchange.getResponseBody().write(response, 0, response.length);
//								aExchange.getResponseBody().close();
								success = true;
								break;
							}
						}
					}
					catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					if ( success ) {
						System.out.println("did the thing");
					}
					else {
						System.out.println("failed to do the thing");
						String response = "message from handler\n";
						aExchange.sendResponseHeaders(HTTPResponseConstants.OK, response.getBytes().length);
						OutputStream os = aExchange.getResponseBody();
						os.write(response.getBytes());
						os.close();
					}
				}

				@Override
				public String getHandlerName() {
					return "tryitSecure";
				}
			});
		} catch (Exception exception) {
		    exception.printStackTrace();;
		    System.err.println("Failed to create HTTPS server on port " + port + " of localhost");
		}
	}
	
	public void createContext(String aContext, HttpHandler aHttpHandler ) {
		httpsServer.createContext(aContext, aHttpHandler);
	}
	
	public void start() {
		httpsServer.start();
	}
	
	public void setExecutor(Executor aExecutor) {
		httpsServer.setExecutor(aExecutor);
	}
}
