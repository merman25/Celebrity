var game_state="WAITING_FOR_PLAYERS"
var num_names_per_player=0
var game_arr = []
var current_name_index=0
var previous_name_index=0
var iAmPlaying = false
var nameList = []
var gameStateLogging = false

function htmlEscape(string) {
    return string.replace( /&/g, "&amp;")
                 .replace( /</g, "&lt;" )
                 .replace( />/g, "&gt;" )
                 .replace( /\"/g, "&quot;" )
                 .replace( /\'/g, "&#39" );
}

function myDecode(string) {
    return htmlEscape( decodeURIComponent( string.replace(/\+/g, " ") ) );
}

function setCookie(cname, cvalue, exdays) {
  var d = new Date();
  d.setTime(d.getTime() + (exdays * 24 * 60 * 60 * 1000));
    var expires = "expires="+d.toUTCString();
    var cookieString = cname + "=" + cvalue + ";" + expires + ";path=/";
  document.cookie = cookieString;
}

function getCookie(cname) {
  var name = cname + "=";
  var ca = document.cookie.split(';');
  for(var i = 0; i < ca.length; i++) {
    var c = ca[i];
    while (c.charAt(0) == ' ') {
      c = c.substring(1);
    }
    if (c.indexOf(name) == 0) {
      return c.substring(name.length, c.length);
    }
  }
  return "";
}

function clearCookie(cname) {
    document.cookie = cname + "=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
}

function nameSubmitted() {
    document.getElementById("divChooseName").style.display = 'none'
    document.getElementById("divJoinOrHost").style.display = 'block'
}

function requestGameID() {
    document.getElementById("join").style.display = 'none'
    document.getElementById("host").style.display = 'none'

    document.getElementById("joinGameForm").style.display = 'block'
}

/*
function provideGameID(oFormElement) {
  var xhr = new XMLHttpRequest();
  xhr.onload = function(){ alert (xhr.responseText); } // success case
  xhr.onerror = function(){ alert (xhr.responseText); } // failure case
  xhr.open (oFormElement.method, oFormElement.action, true);
  xhr.send (new FormData (oFormElement));
  return false;
}
*/

function hostNewGame() {
    document.getElementById("divJoinOrHost").style.display = 'none'
//    document.getElementById("host").style.display = 'none'

    document.getElementById("hostGameDiv").style.display = 'block'
    document.getElementById("playGameDiv").style.display = 'block'
    var xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function() {
	if (this.readyState == 4 && this.status == 200) {
	    var arr = toAssocArr(this.responseText)
	    var gameID = arr["gameID"]
	    updateGameInfo("<hr>\n"
			   + "<h2>Game ID: " + gameID + "</h2>\n"
			   + "<p>Waiting for others to join...</p>" );

	    updateGameStateForever(gameID);
	}
    };
    xhttp.onload = function() {}
    xhttp.open("GET", "hostNewGame", true);
    xhttp.send();
}

function updateGameInfo(html) {
    document.getElementById("gameInfoDiv").innerHTML = html;
}

function toAssocArr(inputText) {
    var keyValArr = inputText.split("&")
    var arr = {};
    for ( var i=0; i<keyValArr.length; i++ ) {
	var element = keyValArr[i]
	var elementSplit = element.split("=")
	if ( elementSplit.length == 2 ) {
	    arr[ elementSplit[0] ] = elementSplit[1]
	}
    }

    return arr;
}

function updateGameStateForever(gameID) {
    setInterval( updateGameState, 500, gameID );
}

function updateGameState(gameID) {
    var xhttp = new XMLHttpRequest();
    xhttp.onreadystatechange = function() {
	if (this.readyState == 4 && this.status == 200) {
	    game_arr = toAssocArr(this.responseText)

	    num_names_per_player = game_arr["numNames"]

	    if ( gameStateLogging ) {
		console.log("names per player: " + num_names_per_player);
	    }
	    
	    iAmPlaying = iAmCurrentPlayer()

	    if ( gameStateLogging ) {
		console.log("Current player? " + iAmPlaying);
	    }

	    var gameStateString = game_arr["state"]
	    if ( game_state != "READY_TO_START_NEXT_TURN"
		 && gameStateString == "READY_TO_START_NEXT_TURN" ) {

		if ( iAmPlaying ) {
		    document.getElementById("startTurnButton").style.display = 'block'
		    document.getElementById("gameStatusDiv").innerHTML = "It's your turn!"
		}
		else {
		    var currentPlayerName = myDecode( game_arr["currentPlayer"] )
		    
		    document.getElementById("startTurnButton").style.display = 'none'
		    document.getElementById("gameStatusDiv").innerHTML = "Waiting for " + currentPlayerName + " to start turn"
		}
		if ( gameStateLogging ) {
		    console.log("finished changes for READY_TO_START_NEXT_TURN");
		}
	    }


	    var nameListString = game_arr["nameList"]
	    if ( nameListString != null ) {
		nameList = nameListString.split(",")
	    }
	    
	    if ( gameStateLogging ) {
		console.log("Processed name list");
	    }

	    setGameState(gameStateString);

	    if ( gameStateLogging ) {
		console.log("Set game state to " + gameStateString);
	    }

	    if ( game_state == "PLAYING_A_TURN" ) {
		document.getElementById("gameStatusDiv").innerHTML = "Seconds remaining: " + game_arr["secondsRemaining"]
	    }

	    var playerListString = game_arr["players"]
			 
	    if ( playerListString != null ) {
		var playerList = playerListString.split(",")

		var htmlList = "<h3>Players</h3>\n<ul>\n"
		for ( var i=0; i<playerList.length; i++) {
		    htmlList += "<li>" + myDecode( playerList[i] ) + "</li>\n"
		}
		htmlList += "</ul>"
		document.getElementById("playerList").innerHTML = htmlList;
	    }
	    else {
		document.getElementById("playerList").innerHTML = "";
	    }

	    if ( gameStateLogging ) {
		console.log("Finished processing player list");
	    }

	    if ( gameStateString == "WAITING_FOR_NAMES" ) {
		var numPlayersToWaitFor = game_arr["numPlayersToWaitFor"]
		if ( numPlayersToWaitFor != null ) {
		    document.getElementById("gameStatusDiv").innerHTML = "Waiting for names from " + numPlayersToWaitFor + " player(s)"
		}
		else {
		    document.getElementById("gameStatusDiv").innerHTML = ""
		}

		if ( numPlayersToWaitFor == null
		     || numPlayersToWaitFor == "0" ) {
		    document.getElementById("startGameButton").style.display = 'block'
		    showHostDutiesElements();
		}
		

		if ( gameStateLogging ) {
		    console.log("Finished processing WAITING_FOR_NAMES");
		}
	    }

	    var teamListString = game_arr["teams"]
	    if ( teamListString != null ) {
		var teamList = teamListString.split(",");
		var tableHeaders = [];
		var tableColumns = [];

		for ( var i=0; i<teamList.length; i++) {
		    var teamString = teamList[i];
		    var teamNameArr = teamString.split("\|")
		    var teamName = teamNameArr[0]

		    tableHeaders[i] = teamName;

		    tableColumns[i] = [];
		    
		    for ( var j=1; j<teamNameArr.length; j++) {
			tableColumns[i][j-1] = myDecode( teamNameArr[j] );
		    }
		}

		var htmlTeamList = "";

		if ( tableHeaders.length > 0 ) {
		    htmlTeamList += "<h2>Teams</h2>\n";
		    htmlTeamList += '<table>\n';

		    htmlTeamList += '<tr>\n';
		    for ( var i=0; i<tableHeaders.length; i++ ) {
			htmlTeamList += '<th>';
			htmlTeamList += tableHeaders[i];
			htmlTeamList += "</th>\n";
		    }
		    htmlTeamList += '</tr>\n';

		    for ( var row=0; ; row++ ) {
			var stillHaveRowsInAtLeastOneColumn = false;
			for ( var col=0; col<tableColumns.length; col++ ) {
			    if ( row < tableColumns[col].length ) {
				stillHaveRowsInAtLeastOneColumn = true;
				break;
			    }
			}

			if ( ! stillHaveRowsInAtLeastOneColumn ) {
			    break;
			}

			htmlTeamList += '<tr>\n';
			for ( var col=0; col<tableColumns.length; col++) {
			    htmlTeamList += '<td>';
			    if ( row < tableColumns[col].length ) {
				htmlTeamList += tableColumns[col][row];
			    }
			    htmlTeamList += "</td>\n";
			}
			htmlTeamList += "</tr>\n";
		    }
		    htmlTeamList += '</table>\n';
		    
		}

		document.getElementById("teamList").innerHTML = htmlTeamList
	    }

	    if ( gameStateLogging ) {
		console.log("Finished processing team list");
	    }

	    var numRounds = game_arr["rounds"]
	    if ( numRounds > 0 ) {
		var htmlParams = "<h2>Settings</h2>\n" +
		    "Rounds: " + numRounds + "<br>\n" +
		    "Round duration (sec): " + game_arr["duration"] + "<br>\n<hr>\n";
		document.getElementById("gameParamsDiv").innerHTML = htmlParams;
	    }

	    if ( gameStateLogging ) {
		console.log("Finished processing numRounds");
	    }

	    var namesAchievedString = game_arr["namesAchieved"]
	    if ( namesAchievedString != null ) {
		scoresHTML = "<h2>Scores</h2>\n";
		scoresHTML += '<div style="display: flex; flex-direction: row;">\n';

		var namesAchievedPerTeamStringArr = namesAchievedString.split(",")
		for ( var i=0; i<namesAchievedPerTeamStringArr.length; i++) {
		    var namesAchievedForTeamString = namesAchievedPerTeamStringArr[i]
		    var namesAchievedForTeam = namesAchievedForTeamString.split("\|");
		    var teamName = namesAchievedForTeam[0]

		    scoresHTML += '<div style="padding-right: 4rem;">\n'
		    scoresHTML += "<h3>" + teamName + "</h3>\n";
		    var score =  namesAchievedForTeam.length - 1;
		    if ( score == NaN ) {
			score = 0;
		    }
		    scoresHTML += "Score: " + score  + "\n";
		    scoresHTML += "<ol>\n"
		    for (var j=1; j<namesAchievedForTeam.length; j++) {
			scoresHTML += "<li>" + myDecode( namesAchievedForTeam[j] ) + "</li>\n"
		    }
		    scoresHTML += "</ol>\n</div>\n";
		}
		scoresHTML += '</div>';

		document.getElementById("scoresDiv").innerHTML = scoresHTML
	    }
	    else {
		document.getElementById("scoresDiv").innerHTML = ""		
	    }

	    if ( gameStateLogging ) {
		console.log("Finished processing namesAchievedString");
	    }

	    var totalScoresString = game_arr["scores"]

	    if ( totalScoresString != null ) {
		totalScoresHTML = ""

		var tableHeaders = [ "Round" ];
		var tableColumns = [[]];

		var totalScoresPerTeamArr = totalScoresString.split(",");
		for ( var i=0; i<totalScoresPerTeamArr.length; i++ ) {
		    var totalScoresForTeamString = totalScoresPerTeamArr[i];
		    var totalScoresForTeam = totalScoresForTeamString.split("\|");
		    var teamName = totalScoresForTeam[0];

		    tableHeaders[i + 1] = teamName;

		    var total = 0;
		    if ( totalScoresForTeam.length > 0 ) {
			tableColumns[i+1] = [];
		    }
		    for ( var j=1; j<totalScoresForTeam.length; j++ ) {
			tableColumns[0][j-1] = j;
			tableColumns[i+1][j-1] = totalScoresForTeam[j];
			total += parseInt(totalScoresForTeam[j]);
		    }
		    if ( totalScoresForTeam.length > 0 ) {
			tableColumns[0][totalScoresForTeam.length-1] = "Total";
			tableColumns[i+1][totalScoresForTeam.length-1] = total;
		    }
		}

		if ( tableColumns[0].length > 1 ) {
		    totalScoresHTML += "<h2>Total Scores</h2>\n";
		    totalScoresHTML += '<table>\n';

		    totalScoresHTML += "<tr>\n";
		    for ( var i=0; i<tableHeaders.length; i++ ) {
			totalScoresHTML += '<th>' + tableHeaders[i] + "</th>";
		    }
		    totalScoresHTML += "</tr>\n";

		    for ( var row=0; row < tableColumns[0].length; row++ ) {
			totalScoresHTML += '<tr>\n';
			for ( var col=0; col<tableColumns.length; col++) {
			    var styleString = '>';
			    if ( row == tableColumns[col].length - 1 ) {
				styleString = ' class="totalClass">';
			    }
			    totalScoresHTML += '<td' + styleString + tableColumns[col][row] + "</td>";
			}
			totalScoresHTML += "</tr>\n";
		    }

		    totalScoresHTML += "</table>";
		}

		document.getElementById("totalScoresDiv").innerHTML = totalScoresHTML;
	    }
	}
    }

    xhttp.open("POST", "requestGameState", true );
    xhttp.send("gameID=" + gameID);
}

function iAmCurrentPlayer() {
    sessionID = getCookie("session");
    var currentPlayerSessionID = game_arr["currentPlayerSession"]
    
    if ( sessionID == currentPlayerSessionID ) {
	return true;
    }
    else {
	return false;
    }
}

function gameParamsSubmitted() {
    document.getElementById("gameParamsForm").style.display = 'none'
}

function allocateTeams() {
    document.getElementById("requestNamesButton").style.display = 'block'
    
    var xhttp = new XMLHttpRequest();
    xhttp.onload = function() {}
    xhttp.open("GET", "allocateTeams", true);
    xhttp.send();
    
}

function waitForGameIDResponse() {
    document.getElementById("divJoinOrHost").style.display = 'none'
    // For some reason it doesn't send the xhttp unless I set a timeout.
    setTimeout( function() {
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
	    if (this.readyState == 4 && this.status == 200) {
		var arr = toAssocArr(this.responseText)
		
		var gameResponse = arr["GameResponse"]
		if ( gameResponse == "OK" ) {
		    document.getElementById("joinGameForm").style.display = 'none'
		    document.getElementById("playGameDiv").style.display = 'block'
		    
		    var gameID = arr["GameID"]

		    updateGameInfo("<hr>\n"
				   + "<h2>Game ID: " + gameID + "</h2>\n"
				   + "<p>Waiting for others to join...</p>" );

		    
		    updateGameStateForever(gameID);
		}
		else {
		    document.getElementById("gameIDErrorDiv").innerHTML = "Unknown Game ID"
		}
	    }
	};
	xhttp.onload = function() {}
	xhttp.open("POST", "askGameIDResponse", true);
	xhttp.send("");
    }, 500 );
}

function requestNames() {
    document.getElementById("requestNamesButton").style.display = 'none'
    document.getElementById("allocateTeamsDiv").style.display = 'none'
    hideHostDutiesElements();
    
    setTimeout( function() {
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
	    if (this.readyState == 4 && this.status == 200) {
		var arr = toAssocArr(this.responseText)
		
		
	    };
	}
	xhttp.onload = function() {}
	xhttp.open("POST", "sendNameRequest", true);
	xhttp.send("a=b");
	
    }, 500 );
}

function setGameState(newState) {
    if ( game_state != newState ) {
	console.log("Changing state from " + game_state + " to " + newState);
    }
	 
    if ( game_state != "WAITING_FOR_NAMES"
	 && newState == "WAITING_FOR_NAMES" ) {
	updateGameInfo("<hr>Put your names into the hat!");
	addNameRequestForm();
    }

    if ( game_state != "PLAYING_A_TURN"
	 && newState == "PLAYING_A_TURN" ) {
	current_name_index=game_arr["currentNameIndex"]
	previous_name_index=game_arr["previousNameIndex"]

	if ( iAmPlaying ) {
	    document.getElementById("turnControlsDiv").style.display = 'flex'
	}
	updateCurrentNameDiv();
    }

    if ( game_state != "READY_TO_START_NEXT_TURN"
	 && newState == "READY_TO_START_NEXT_TURN" ) {
	document.getElementById("turnControlsDiv").style.display = 'none'
	document.getElementById("currentNameDiv").innerHTML = ""
	var roundIndex = game_arr["roundIndex"]
	if ( roundIndex != null ) {
	    roundIndex = parseInt(roundIndex) + 1;
	}
	else {
	    roundIndex = "??"
	}
	updateGameInfo("<hr>Playing round " + roundIndex + " of " + game_arr["rounds"])
    }

    if ( newState == "READY_TO_START_NEXT_ROUND" ) {
	document.getElementById("gameStatusDiv").innerHTML = "Finished Round! See scores below";
	showHostDutiesElements();
	document.getElementById("startNextRoundButton").style.display = 'block'
    }
    else {
	document.getElementById("startNextRoundButton").style.display = 'none'
    }

    if ( newState != "READY_TO_START_NEXT_ROUND"
	 && game_state == "READY_TO_START_NEXT_ROUND" ) {
	hideHostDutiesElements();
    }

    if ( newState == "ENDED"
	 && game_state != "ENDED" ) {
	updateGameInfo("<hr>Game Over!");
    }

    game_state = newState
}

function addNameRequestForm() {
    var html = '<form id="nameListForm" method="post" onsubmit="nameListSubmitted()">\n'
    for (var i=1; i<=num_names_per_player; i++) {
	html += '<div class="col-label">\n';
	html += '<label for="name' + i + '">Name ' + i + '</label>\n'
	html += '</div>\n';

	html += '<div class="col-textfield">\n';
	html += '<input id="name' + i + '" name="name' + i + '" type="text"><br>\n'
	html += '</div>\n';
    }

    html += '<input type="hidden" name="form" value="nameList">\n'
    html += '<input type="submit" value="Put in Hat">\n'
    html += '</form>\n'

    document.getElementById("nameList").innerHTML = html
    
}

function nameListSubmitted() {
    setTimeout( function() {
	var element = document.getElementById("nameList");
	element.style.display = 'none'
    }, 200 );
}

function hideHostDutiesElements() {
    performHostDutiesElements = document.querySelectorAll(".performHostDutiesClass");
    for ( var i=0; i<performHostDutiesElements.length; i++) {
	performHostDutiesElements[i].style.display = 'none';
    }
}

function showHostDutiesElements() {
    performHostDutiesElements = document.querySelectorAll(".performHostDutiesClass");
    for ( var i=0; i<performHostDutiesElements.length; i++) {
	performHostDutiesElements[i].style.display = 'block';
    }
}

function startGame() {
    setTimeout( function() {
	document.getElementById("startGameButton").style.display = 'none'
	document.getElementById("gameStatusDiv").innerHTML = ""

	hideHostDutiesElements();

	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
	    if (this.readyState == 4 && this.status == 200) {
		var arr = toAssocArr(this.responseText)
		
		
	    };
	}
	xhttp.onload = function() {}
	xhttp.open("POST", "startGame", true);
	xhttp.send("");
	
    }, 500 );
}

function startTurn() {
    setTimeout( function() {
	document.getElementById("startTurnButton").style.display = 'none'
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
	    if (this.readyState == 4 && this.status == 200) {
		var arr = toAssocArr(this.responseText)
		
		
	    };
	}
	xhttp.onload = function() {}
	xhttp.open("POST", "startTurn", true);
	xhttp.send("");
	
    }, 500 );
}

function updateCurrentNameDiv() {
    if ( iAmPlaying ) {
	currentName = myDecode( nameList[ current_name_index ] );
	document.getElementById("currentNameDiv").innerHTML = "Name: " + currentName
    }
    else {
	document.getElementById("currentNameDiv").innerHTML = ""
//	var numOfNameBeingGuessed = current_name_index - previous_name_index + 1;
//	document.getElementById("currentNameDiv").innerHTML = game_arr["currentPlayer"] + " is on name " + numOfNameBeingGuessed
    }
}

function gotName() {
//    gameStateLogging = true;
    current_name_index++;
    setTimeout( function() {
	document.getElementById("startTurnButton").style.display = 'none'
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
	    if (this.readyState == 4 && this.status == 200) {
		var arr = toAssocArr(this.responseText)
	    };
	}
	xhttp.onload = function() {}
	xhttp.open("POST", "setCurrentNameIndex", true);
	xhttp.send("newNameIndex=" + current_name_index);
	
    }, 500 );

    if ( current_name_index < nameList.length ) {
	updateCurrentNameDiv();
    }
    else {
	document.getElementById("turnControlsDiv").style.display = 'none'
	finishRound();
    }
}

function finishRound() {
    document.getElementById("gameStatusDiv").innerHTML = "Finished Round!"
    document.getElementById("currentNameDiv").innerHTML = ""
}

function startNextRound() {
       setTimeout( function() {
	document.getElementById("startNextRoundButton").style.display = 'none'
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
	    if (this.readyState == 4 && this.status == 200) {
		var arr = toAssocArr(this.responseText)
		
		
	    };
	}
	xhttp.onload = function() {}
	xhttp.open("POST", "startNextRound", true);
	xhttp.send("");
	
    }, 500 );
}

function pass() {
    document.getElementById("passButton").disabled = true;
    setTimeout( function() {
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {
	    console.log("pass readyState" + this.readyState + ", status " + this.status);
	    if (this.readyState == 4 && this.status == 200) {
		document.getElementById("passButton").disabled = false;
		var arr = toAssocArr(this.responseText)
		var nameListString = arr["nameList"];
		if ( nameListString != null ) {
		    nameList = nameListString.split(",");
		    updateCurrentNameDiv();
		}
	    };
	}
	xhttp.onload = function() {}
	xhttp.open("POST", "pass", true);
	xhttp.send("passNameIndex=" + current_name_index);
	
    }, 500 );
}

function endTurn() {
    document.getElementById("turnControlsDiv").style.display = 'none';
    setTimeout ( function() {
	var xhttp = new XMLHttpRequest();
	xhttp.onreadystatechange = function() {};
	xhttp.onload = function() {};
	xhttp.open("POST", "endTurn", true);
	xhttp.send("");
    }, 500 );
}
