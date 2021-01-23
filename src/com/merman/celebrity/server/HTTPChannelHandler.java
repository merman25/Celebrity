package com.merman.celebrity.server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

import com.merman.celebrity.server.logging.Log;
import com.merman.celebrity.server.logging.LogMessageSubject;
import com.merman.celebrity.server.logging.LogMessageType;

public class HTTPChannelHandler {
	public boolean stop = false;
	
	private Selector selector;
	private Map<SelectionKey, SocketChannel>		mapSelectionKeysToClientChannels		= new HashMap<>();
	private Map<SocketChannel, HTTPExchange>		mapSocketChannelsToHTTPExchanges		= new HashMap<>();
	
	private ByteBuffer readWriteBuffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer. As of 16/1/21, total size of client directory is 312 kB, so 1 MB is loads.
	
	private class MyReadFromChannelRunnable
	implements Runnable {

		@Override
		public void run() {
			try {
				while (! stop) {
					try {
						selector.select();
					} catch (IOException e) {
						Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException on selector", selector, "exception", e);

						// We're probably screwed at this point, but may as well try again. Put in a sleep to avoid blasting the log files with data
						try {
							Thread.sleep(3000);
						} catch (InterruptedException e1) {}

						continue;
					}
					
					synchronized (HTTPChannelHandler.this) {
						if (! stop) {
							// if selector.select() was woken up by a real selection, and not by invocation of selector.close()
							for (SelectionKey key : selector.selectedKeys()) {
								if (key.isReadable()) {
									SocketChannel clientChannel = mapSelectionKeysToClientChannels.get(key);
									if (clientChannel == null) {
										// Shouldn't ever happen, but I'm copying some example code
										Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "Readable key", key, "from this server's selector", selector, "could not find SocketChannel");
										continue;
									}
									
									try {
										int bytesRead = clientChannel.read(readWriteBuffer.clear());
										if (bytesRead < 0) {
											key.cancel();
											mapSelectionKeysToClientChannels.remove(key);
											mapSocketChannelsToHTTPExchanges.remove(clientChannel);
										}
										else if (bytesRead > 0) {
											// We do sometimes read 0 bytes, for whatever reason
											byte[] byteArr = new byte[bytesRead];
											readWriteBuffer.flip();
											readWriteBuffer.get(byteArr);
											
											HTTPExchange exchange = mapSocketChannelsToHTTPExchanges.computeIfAbsent(clientChannel, sc -> new HTTPExchange(sc));
											exchange.addBytes(byteArr);
											
											if (exchange.isFinishedReadingRequestHeaders()) {
												Log.log(LogMessageType.INFO, LogMessageSubject.GENERAL, "=== Finished Reading Request Headers ===");
												exchange.getRequestHeaders().entrySet().stream().forEach(mapEntry -> System.out.format("%s: %s\n", mapEntry.getKey(), mapEntry.getValue()));

//												String response = "HTTP/1.1 200 OK\r\n" +
//														"Date: Sat, 16 Jan 2021 18:52:27 GMT\r\n" +
//														"Content-length: 33\r\n" +
//														"\r\n" +
//														"<html>Here is your website</html>";
//
//												readWriteBuffer.clear();
//												readWriteBuffer.put(response.getBytes(StandardCharsets.US_ASCII));
//												readWriteBuffer.flip();
//												clientChannel.write(readWriteBuffer);
//
//												clientChannel.close();
//												key.cancel();
//												mapSelectionKeysToClientChannels.remove(key);
//												mapSocketChannelsToHTTPExchanges.remove(clientChannel);
											}
										}

									} catch (IOException e) {
										Log.log(LogMessageType.ERROR, LogMessageSubject.HTTP_REQUESTS, "IOException when reading clientChannel ", e); // TODO associate with Session and/or IP
									}
									
//									Scanner scanner = new Scanner(clientChannel);
//									scanner.useDelimiter("\\r\\n");
//									while (scanner.hasNext()) {
//										System.out.println(scanner.next());
//									}
								}
							}
						}
					}
				}
			}
			catch (RuntimeException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "Exception in HTTP Channel Handler", e);
			}
		}
		
	}

	public void add(SocketChannel aClientSocketChannel) throws IOException {
		if (selector == null) {
			start();
		}
		
		aClientSocketChannel.configureBlocking(false);
		SelectionKey clientKey = aClientSocketChannel.register(selector, SelectionKey.OP_READ);
		mapSelectionKeysToClientChannels.put(clientKey, aClientSocketChannel);
		selector.wakeup();
	}
	
	protected synchronized void start() throws IOException {
		if (selector == null) {
			selector = Selector.open();
			new Thread(new MyReadFromChannelRunnable(), "HTTPChannelHandler").start();
			
//			new JFrame("HTTP Server") {{
//				getContentPane().add( new JPanel() {{
//					add( new JButton( new AbstractAction("Stop") {
//
//						@Override
//						public void actionPerformed(ActionEvent aArg0) {
//							HTTPChannelHandler.this.stop();
//						}
//					}) );
//				}} );
//
//				pack();
//				setLocationRelativeTo(null);
//				setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//				setVisible(true);
//			}};
		}
	}
	
	public synchronized void stop() {
		stop = true;
		if (selector != null) {
			try {
				selector.close();
			} catch (IOException e) {
				Log.log(LogMessageType.ERROR, LogMessageSubject.GENERAL, "IOException when closing HTTPChannelHandler Selector", e);
			}
		}
	}
}
