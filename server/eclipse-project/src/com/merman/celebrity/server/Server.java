package com.merman.celebrity.server;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;

import com.merman.celebrity.game.Game;
import com.merman.celebrity.game.GameManager;
import com.merman.celebrity.game.GameState;
import com.merman.celebrity.game.Player;
import com.merman.celebrity.server.handlers.AHttpHandler;
import com.merman.celebrity.server.handlers.FormHandler;
import com.merman.celebrity.server.handlers.FormHandlerRegistry;
import com.merman.celebrity.server.handlers.HttpExchangeUtil;
import com.merman.celebrity.server.handlers.ServeFileHandler;
import com.merman.celebrity.server.handlers.TestHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class Server {
	public static final Path CLIENT_FILE_DIRECTORY = new File( "../../client" ).toPath();
	
	private String IPAddress = "192.168.1.17";
	private int portNumber = 8080;
	
	private class ListenForNewConnectionsRunnable
	implements Runnable {

		@Override
		public void run() {
			while ( true ) {
				try {
					ServerSocket		socket		= new ServerSocket();
					socket.setSoTimeout(1000);
					socket.bind( new InetSocketAddress( InetAddress.getByName( IPAddress ), portNumber ));
				}
				catch ( Exception e ) {
					e.printStackTrace();
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
	}

	public void start() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress( portNumber ), 10);

		Files.walk( CLIENT_FILE_DIRECTORY )
			 .forEach( path -> {
				 if ( Files.isRegularFile(path) ) {
					 String pathAsString = CLIENT_FILE_DIRECTORY.relativize(path).toString();
					 System.out.println( "adding file: " + pathAsString );
					server.createContext("/" + pathAsString, new ServeFileHandler(pathAsString));
				 }
			 } );

		System.out.println("\n\n");

		FormHandlerRegistry.addHandler( new FormHandler("username" , exchange -> {
			try {
				String sessionID = HttpExchangeUtil.getSessionID( exchange );
			LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(exchange);
			String userName = requestBody.get("username");
			Session session = SessionManager.getSession( sessionID );
			if ( session != null ) {
				System.out.println( "Setting user name " + userName + " to session " + session );
				session.getPlayer().setName(userName);
			}
			else {
				System.err.println( "Session not found: " + sessionID );
			}
			}
			catch ( Exception e ) {
				e.printStackTrace();
			}
		} ) );

		FormHandlerRegistry.addHandler( new FormHandler( "gameID" ) {
			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID( aExchange );
				LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(aExchange);
				String gameID = requestBody.get("gameID");
				Session session = SessionManager.getSession( sessionID );
				if ( session != null ) {
					System.out.println( "Session " + session + " [" + session.getPlayer().getName() + "] wants to join game " + gameID );
					Game game = GameManager.getGame(gameID);
					if ( game != null ) {
						game.addPlayer( session.getPlayer() );
					}
//					else {
//						sendResponse(aExchange, 206, "<script>console.log( \"test 206\" );</script>" );
//					}
				}
				else {
					System.err.println( "Session not found: " + sessionID );
				}
			}
		} );

		server.createContext( "/hostNewGame", new AHttpHandler() {

			@Override
			public String getHandlerName() {
				return "hostNewGame";
			}

			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID( aExchange );
				Session session = SessionManager.getSession( sessionID );
				if ( session != null ) {
					System.out.println( "Session " + session + " [" + session.getPlayer().getName() + "] will host a game" );
					Game game = GameManager.createGame(session.getPlayer());

					sendResponse(aExchange, HTTPResponseConstants.OK, "gameID=" + game.getID());
				}
				else {
					System.err.println( "Session not found: " + sessionID );
				}
			}
		});

		server.createContext( "/requestGameState", new AHttpHandler() {

			@Override
			public String getHandlerName() {
				return "requestGameState";
			}

			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
//				dumpRequest(aExchange);
				LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(aExchange);
				String gameID = requestBody.get("gameID");
				if ( gameID != null ) {
					Game game = GameManager.getGame(gameID);
					if ( game != null ) {
//						System.out.format("Passing state of game %s [%s] to Player %s\n", game.getID(), game.getState(), SessionManager.getSession( HttpExchangeUtil.getSessionID(aExchange) ).getPlayer().getName() );
						String serialisedGame = GameManager.serialise(game);
						sendResponse(aExchange, HTTPResponseConstants.OK, serialisedGame);
					}
				}
			}
		});

		FormHandlerRegistry.addHandler( new FormHandler( "gameParams", exchange -> {
			String sessionID = HttpExchangeUtil.getSessionID(exchange);
			Session session = SessionManager.getSession(sessionID);
			if ( session != null ) {
				Player player = session.getPlayer();
				Game game = GameManager.getGameHostedByPlayer(player);

				if ( game != null ) {
					LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(exchange);
					String numRoundsString = requestBody.get("numRounds");
					if ( numRoundsString == null ) {
						System.err.println( "No value for numRounds" );
					}
					else {
						try {
							int numRounds = Integer.parseInt( numRoundsString );
							if ( numRounds <= 0 || numRounds > 10 ) {
								System.err.println( "numRounds " + numRounds + ", should be 1-10" );
							}
							else {
								game.setNumRounds(numRounds);
							}
						}
						catch ( NumberFormatException e ) {
							e.printStackTrace();
						}
					}

					String roundDurationString = requestBody.get("roundDuration");
					if ( roundDurationString == null ) {
						System.err.println( "No value for roundDuration" );
					}
					else {
						try {
							int roundDurationInSec = Integer.parseInt( roundDurationString );
							if ( roundDurationInSec <= 0 || roundDurationInSec > 600 ) {
								System.err.println( "roundDuration " + roundDurationInSec + ", should be 1-600" );
							}
							else {
								game.setRoundDurationInSec(roundDurationInSec);
							}
						}
						catch ( NumberFormatException e ) {
							e.printStackTrace();
						}
					}

					String numNamesString = requestBody.get("numNames");
					if ( numNamesString == null ) {
						System.err.println( "No value for numNames" );
					}
					else {
						try {
							int numNames = Integer.parseInt( numNamesString );
							if ( numNames <= 0 || numNames > 10 ) {
								System.err.println( "numNames " + numNames + ", should be 1-10" );
							}
							else {
								game.setNumNamesPerPlayer(numNames);
							}
						}
						catch ( NumberFormatException e ) {
							e.printStackTrace();
						}
					}


				}
				else {
					System.err.println( player + " is not hosting any game" );
				}
			}
			else {
				System.err.println( "Unknown sessionID for gameParams: " + sessionID );
			}
		} ) );

		server.createContext( "/allocateTeams", new AHttpHandler() {

			@Override
			public String getHandlerName() {
				return "allocateTeams";
			}

			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					Game game = GameManager.getGameHostedByPlayer(player);

					if ( game != null ) {
						System.out.println( "allocating teams" );
						game.allocateTeams();
						sendResponse(aExchange, HTTPResponseConstants.OK, "");
					}
					else {
						System.err.println( player + " is not hosting any game" );
					}
				}
				else {
					System.err.println( "Unknown sessionID for allocateTeams: " + sessionID );
				}
			}
		}) ;

		server.createContext( "/askGameIDResponse", new AHttpHandler() {

			@Override
			public String getHandlerName() {
				return "askGameIDResponse";
			}

			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					String gameIDResponse;
					if ( player.getGame() != null ) {
						gameIDResponse = "GameResponse=OK&GameID=";
					}
					else {
						gameIDResponse = "GameResponse=NotFound";
					}
					System.out.println( "sending gameID response: " + gameIDResponse );
					sendResponse(aExchange, HTTPResponseConstants.OK, gameIDResponse + player.getGame().getID() );
				}
				else {
					System.err.println( "Unknown sessionID for askGameIDResponse: " + sessionID );
				}
			}
		});
		
		server.createContext( "/sendNameRequest", new AHttpHandler() {
			
			@Override
			public String getHandlerName() {
				return "sendNameRequest";
			}
			
			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					String gameIDResponse;
					if ( player.getGame() != null ) {
						player.getGame().setState(GameState.WAITING_FOR_NAMES);
					}
				}
			}
		} );
		
		FormHandlerRegistry.addHandler( new FormHandler("nameList", exchange -> {
			String sessionID = HttpExchangeUtil.getSessionID(exchange);
			Session session = SessionManager.getSession(sessionID);
			if ( session != null ) {
				Player player = session.getPlayer();
				if ( player != null ) {
					Game game = player.getGame();
					if ( game != null
							&& game.getState() == GameState.WAITING_FOR_NAMES ) {
						int numNamesPerPlayer = game.getNumNamesPerPlayer();
						LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(exchange);
						List<String>		celebNameList		= new ArrayList<>();
						for (int i = 1; i <= numNamesPerPlayer; i++) {
							String celebName = requestBody.get("name" + i);
							if ( celebName != null
									&& ! celebName.trim().isEmpty() ) {
								celebNameList.add(celebName);
							}
						}
						
						game.setNameList( player, celebNameList );
					}
				}
			}
		} ) );
		
		server.createContext( "/startGame", new AHttpHandler() {
			
			@Override
			public String getHandlerName() {
				return "startGame";
			}
			
			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					Game gameHostedByPlayer = GameManager.getGameHostedByPlayer(player);
					if ( gameHostedByPlayer != null
							&& gameHostedByPlayer.getState() == GameState.WAITING_FOR_NAMES ) {
						gameHostedByPlayer.shuffleNames();
						gameHostedByPlayer.allowNextPlayerToStartNextTurn();
						
						sendResponse(aExchange, HTTPResponseConstants.OK, "");
					}
				}
			}
		} );
		
		server.createContext( "/startTurn", new AHttpHandler() {
			
			@Override
			public String getHandlerName() {
				return "startTurn";
			}
			
			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				System.out.println( "starting turn" );
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					Game game = player.getGame();
					game.startTurn();
					
					sendResponse(aExchange, HTTPResponseConstants.OK, "");
				}
			}
		} );

		server.createContext( "/startNextRound", new AHttpHandler() {
			
			@Override
			public String getHandlerName() {
				return "startNextRound";
			}
			
			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				System.out.println( "starting next round" );
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					Game game = player.getGame();
					game.startRound();
					
					sendResponse(aExchange, HTTPResponseConstants.OK, "");
				}
			}
		} );

//		server.createContext( "/finishRound", new AHttpHandler() {
//
//			@Override
//			public String getHandlerName() {
//				return "finishRound";
//			}
//
//			@Override
//			protected void _handle(HttpExchange aExchange) throws IOException {
//				System.out.println( "finishing round" );
//
//				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
//				Session session = SessionManager.getSession(sessionID);
//				if ( session != null ) {
//					Player player = session.getPlayer();
//					Game game = player.getGame();
//					game.stopTurn();
//				}
//			}
//		} );

		server.createContext( "/setCurrentNameIndex", new AHttpHandler() {
			
			@Override
			public String getHandlerName() {
				return "setCurrentNameIndex";
			}
			
			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					Game game = player.getGame();
					LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(aExchange);
					String newNameIndexString = requestBody.get("newNameIndex");
					if ( newNameIndexString != null ) {
						try {
							int newNameIndex = Integer.parseInt(newNameIndexString);
							System.out.println( "setting current name index to " + newNameIndex );
							game.setCurrentNameIndex( newNameIndex );
							
							sendResponse(aExchange, HTTPResponseConstants.OK, "");
						}
						catch ( NumberFormatException e ) {
							e.printStackTrace();
						}
						
					}
				}
			}
		} );

		server.createContext( "/pass", new AHttpHandler() {
			
			@Override
			public String getHandlerName() {
				return "pass";
			}
			
			@Override
			protected void _handle(HttpExchange aExchange) throws IOException {
				String sessionID = HttpExchangeUtil.getSessionID(aExchange);
				Session session = SessionManager.getSession(sessionID);
				if ( session != null ) {
					Player player = session.getPlayer();
					Game game = player.getGame();
					LinkedHashMap<String, String> requestBody = HttpExchangeUtil.getRequestBodyAsMap(aExchange);
					String passNameIndexString = requestBody.get("passNameIndex");
					if ( passNameIndexString != null ) {
						try {
							int passNameIndex = Integer.parseInt(passNameIndexString);
							game.setPassOnNameIndex( passNameIndex );
							
							sendResponse(aExchange, HTTPResponseConstants.OK, "dummy=" + Math.random() + "&nameList=" + String.join(",", game.getShuffledNameList()));
						}
						catch ( NumberFormatException e ) {
							e.printStackTrace();
						}
						
					}
				}
			}
		} );

		String[] requestNames = { "" };
		for ( String requestName : requestNames ) {
			server.createContext( "/" + requestName, new TestHandler(requestName) );
		}
		
        server.setExecutor( Executors.newFixedThreadPool(10) );
        server.start();
	}
}
