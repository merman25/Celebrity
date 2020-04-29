let gameStatus = "WAITING_FOR_PLAYERS";
let numNamesPerPlayer = 0;
let gameStateObject = {};
let currentNameIndex = 0;
let previousNameIndex = 0;
let iAmPlaying = false;
let nameList = [];
let gameStateLogging = false;
let teamList = [];
let webSocket = null;
let useSocket = true;
let firstSocketMessage = true;
let gameID = null;
let myPlayerID = null;

/* Might be useful later: event when DOM is fully loaded 
document.addEventListener('DOMContentLoaded', () => {} );
*/

function htmlEscape(string) {
	return string.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/\"/g, "&quot;")
		.replace(/\'/g, "&#39");
}

function myDecode(string) {
	return htmlEscape(decodeURIComponent(string.replace(/\+/g, " ")));
}

function submitName() {
	document.getElementById("divChooseName").style.display = 'none';
	document.getElementById("divJoinOrHost").style.display = 'block';

	const username = document.getElementById('nameField').value;
	console.log('username', username);

	fetch('username', { method: 'POST', body: 'username=' + username })
		.catch(err => console.error(err));

	tryToOpenSocket();
}

function requestGameID() {
	document.getElementById("join").style.display = 'none';
	document.getElementById("host").style.display = 'none';

	document.getElementById("joinGameForm").style.display = 'block';
}


function hostNewGame() {
	document.getElementById("divJoinOrHost").style.display = 'none';

	document.getElementById("hostGameDiv").style.display = 'block';
	document.getElementById("playGameDiv").style.display = 'block';

	fetch('hostNewGame')
		.then(result => result.json())
		.then(result => {
			gameID = result.gameID;

			document.getElementById("gameIDDiv").innerHTML = '<hr><h2>Game ID: ' + gameID + '</h2>';
			updateGameInfo('<p>Waiting for others to join...</p>');

			if (!useSocket) {
				updateGameStateForever(gameID);
			}
		})
		.catch(err => console.error(err));
}

function tryToOpenSocket() {
	try {
		if (useSocket) {
			const currentURL = window.location.href;
			const currentHostName = currentURL.replace(/^[a-zA-Z]+:\/\//, '')
				.replace(/:[0-9]+.*$/, '');
			webSocket = new WebSocket('ws://' + currentHostName + ':8001/');
			webSocket.onerror = evt => {
				console.error('Error in websocket');
				console.error(evt);
				useSocket = false;
			};
			webSocket.onclose = evt => {
				console.log('websocket closed');
				console.log(evt);
				useSocket = false;
				if (gameID != null) {
					updateGameStateForever(gameID);
				}
			};
			webSocket.onopen = event => {
				console.log('websocket opened');
				const sessionID = getCookie('session');
				if (sessionID == null) {
					sessionID = 'UNKNOWN';
				}
				webSocket.send('session=' + sessionID);

			};

			webSocket.onmessage = evt => {
				const message = evt.data;
				if (firstSocketMessage) {
					firstSocketMessage = false;
					if (message == 'gotcha') {
						useSocket = true;
						console.log('websocket is providing data');
					}

				}
				else if (message.indexOf('GameState=') === 0) {
					console.log('received game state in new way');
					const gameStateString = message.substring('GameState='.length, message.length);
					const gameObj = JSON.parse(gameStateString);
					processGameStateObject(gameObj);
				}
				else if (message.indexOf('MillisRemaining=') === 0) {
					const millisRemainingString = message.substring('MillisRemaining='.length, message.length);
					const millisRemaining = parseInt(millisRemainingString);
					const secondsRemaining = Math.ceil(millisRemaining / 1000);

					updateCountdownClock(secondsRemaining);
				}
				else if (evt.data !== 'client-pong') {
					console.log('message: ' + evt.data);
				}
			};

			const pinger = setInterval(() => {
				if (useSocket) {
					// console.log('sending client ping');
					webSocket.send('client-ping');
				}
				else {
					clearInterval(pinger);
				}
			}, 10000);
		}
	} catch (err) {
		console.error(err);
	}

}

function updateGameInfo(html) {
	document.getElementById("gameInfoDiv").innerHTML = html;
}

function toAssocArr(inputText) {
	let keyValArr = inputText.split("&");
	let arr = {};
	for (let i = 0; i < keyValArr.length; i++) {
		let element = keyValArr[i];
		let elementSplit = element.split("=");
		if (elementSplit.length == 2) {
			arr[elementSplit[0]] = elementSplit[1];
		}
	}

	return arr;
}

function updateGameStateForever(gameID) {
	setInterval(updateGameState, 500, gameID);
}

function updateGameState(gameID) {
	fetch('requestGameState', { method: 'POST', body: 'gameID=' + gameID })
		.then(result => result.json())
		.then(result => {
			processGameStateObject(result);
		})
		.catch(err => console.error(err));

}

function processGameStateObject(newGameStateObject) {
	gameStateObject = newGameStateObject;

	numNamesPerPlayer = gameStateObject.numNames;
	myPlayerID = gameStateObject.publicIDOfRecipient;
	iAmPlaying = iAmCurrentPlayer();
	let iAmHosting = iAmHost();

	let gameStatusString = gameStateObject.status;
	if (gameStatusString == "READY_TO_START_NEXT_TURN") {

		if (iAmPlaying) {
			document.getElementById("startTurnButton").style.display = 'block';
			document.getElementById("gameStatusDiv").innerHTML = "It's your turn!";
		}
		else {
			document.getElementById("startTurnButton").style.display = 'none';

			const currentPlayer = gameStateObject.currentPlayer;
			if (currentPlayer != null) {
				let currentPlayerName = myDecode(currentPlayer.name);

				document.getElementById("gameStatusDiv").innerHTML = "Waiting for " + currentPlayerName + " to start turn";
			}
			else {
				document.getElementById("gameStatusDiv").innerHTML = 'I don\'t know whose turn it is any more :(';
			}
		}
	}
	else {
		document.getElementById("startTurnButton").style.display = 'none';
	}


	nameList = gameStateObject.nameList;
	setGameStatus(gameStatusString);

	if (gameStatus == "PLAYING_A_TURN") {
		updateCountdownClock(gameStateObject.secondsRemaining);
	}


	let playerList = gameStateObject.players;

	if (playerList.length > 0) {
		let htmlList = '<h3>Players</h3>\n<ul id="teamlessPlayerUl">\n';

		let spanClassString = '';
		if (iAmHosting) {
			spanClassString = ' class="rightClickable"';
		}
		for (let i = 0; i < playerList.length; i++) {
			htmlList += `<li class="teamlessPlayerLiClass"><span playerID="${playerList[i].publicID}"${spanClassString}>${myDecode(playerList[i].name)}</span></li>\n`;
		}
		htmlList += '</ul>';
		document.getElementById("playerList").innerHTML = htmlList;

		let teamObjectList = gameStateObject.teams;
		teamList = [];
		if (teamObjectList.length > 0) {
			let tableColumns = [];
			let playerIDs = [];

			for (let i = 0; i < teamObjectList.length; i++) {
				let teamObject = teamObjectList[i];
				let teamName = teamObject.name;

				teamList[i] = teamName;
			}
		}

		if (iAmHosting
			&& teamList.length > 0) {
			let teamlessPlayerLiElements = document.querySelectorAll(".teamlessPlayerLiClass");
			for (let i = 0; i < teamlessPlayerLiElements.length; i++) {
				let teamlessPlayerLi = teamlessPlayerLiElements[i];

				teamlessPlayerLi.addEventListener("contextmenu", event => {
					let playerID = event.target.getAttribute("playerID");
					event.preventDefault();

					let menuHTML = '<ul id="contextMenuForTeamlessPlayer" class="contextMenuClass">';
					menuHTML += '<li class="menuItem" id="removeTeamlessPlayerFromGame">Remove From Game</li>';
					menuHTML += '<li class="separator"></li>';

					for (let j = 0; j < teamList.length; j++) {
						menuHTML += '<li id="changeToTeam' + j + '" class="menuItem">';
						menuHTML += 'Put in ' + teamList[j];
						menuHTML += '</li>';
					}

					menuHTML += '</ul>';

					document.getElementById("teamlessPlayerContextMenuDiv").innerHTML = menuHTML;

					for (let j = 0; j < teamList.length; j++) {
						document.getElementById("changeToTeam" + j).addEventListener("click", event => {
							putInTeam(playerID, j);
							hideAllContextMenus();
						});
					}

					document.getElementById("removeTeamlessPlayerFromGame").addEventListener("click", event => {
						removeFromGame(playerID);
						hideAllContextMenus();
					});

					let contextMenu = document.getElementById("contextMenuForTeamlessPlayer");
					contextMenu.style.display = 'block';
					contextMenu.style.left = (event.pageX - 10) + "px";
					contextMenu.style.top = (event.pageY - 10) + "px";


				});
			}


		}
	}
	else {
		document.getElementById("playerList").innerHTML = "";
	}


	if (gameStatusString == "WAITING_FOR_NAMES") {
		let numPlayersToWaitFor = gameStateObject.numPlayersToWaitFor;
		if (numPlayersToWaitFor != null) {
			document.getElementById("gameStatusDiv").innerHTML = "Waiting for names from " + numPlayersToWaitFor + " player(s)";
		}
		else {
			document.getElementById("gameStatusDiv").innerHTML = "";
		}

		if (numPlayersToWaitFor == null
			|| numPlayersToWaitFor == "0") {
			document.getElementById("startGameButton").style.display = 'block';
			showHostDutiesElements();
		}
	}

	let teamObjectList = gameStateObject.teams;
	teamList = [];
	if (teamObjectList.length > 0) {
		let tableColumns = [];
		let playerIDs = [];

		for (let i = 0; i < teamObjectList.length; i++) {
			let teamObject = teamObjectList[i];
			let teamName = teamObject.name;

			teamList[i] = teamName;

			tableColumns[i] = [];
			playerIDs[i] = [];

			let teamPlayerList = teamObject.playerList;
			for (let j = 0; j < teamPlayerList.length; j++) {
				tableColumns[i][j] = myDecode(teamPlayerList[j].name);
				playerIDs[i][j] = teamPlayerList[j].publicID;
			}
		}

		let htmlTeamList = "";

		if (teamList.length > 0) {
			htmlTeamList += "<h2>Teams</h2>\n";
			htmlTeamList += '<table>\n';

			htmlTeamList += '<tr>\n';
			for (let i = 0; i < teamList.length; i++) {
				htmlTeamList += '<th>';
				htmlTeamList += teamList[i];
				htmlTeamList += "</th>\n";
			}
			htmlTeamList += '</tr>\n';

			for (let row = 0; ; row++) {
				let stillHaveRowsInAtLeastOneColumn = false;
				for (let col = 0; col < tableColumns.length; col++) {
					if (row < tableColumns[col].length) {
						stillHaveRowsInAtLeastOneColumn = true;
						break;
					}
				}

				if (!stillHaveRowsInAtLeastOneColumn) {
					break;
				}

				let tdExtraClassString = '';
				if (iAmHosting) {
					tdExtraClassString = ' rightClickable';
				}

				htmlTeamList += '<tr>\n';
				for (let col = 0; col < tableColumns.length; col++) {
					htmlTeamList += '<td class="playerInTeamTDClass' + tdExtraClassString + '"';
					if (row < tableColumns[col].length) {
						let playerID = playerIDs[col][row];
						htmlTeamList += ' playerID="' + playerID + '" teamIndex="' + col + '">';
						htmlTeamList += tableColumns[col][row];
					}
					else {
						htmlTeamList += '>';
					}
					htmlTeamList += "</td>\n";
				}
				htmlTeamList += "</tr>\n";
			}
			htmlTeamList += '</table>\n';

		}

		document.getElementById("teamList").innerHTML = htmlTeamList;

		if (iAmHosting) {
			let playerInTeamTDElements = document.querySelectorAll(".playerInTeamTDClass");
			for (let i = 0; i < playerInTeamTDElements.length; i++) {
				let playerInTeamTD = playerInTeamTDElements[i];

				playerInTeamTD.addEventListener("contextmenu", event => {
					event.preventDefault();
					let playerIDOfPlayerInTeam = event.target.getAttribute("playerID");
					let teamIndex = parseInt(event.target.getAttribute("teamIndex"));


					let menuHTML = '<ul id="playerInTeamContextMenu" class="contextMenuClass">';
					menuHTML += '<li class="menuItem" id="removePlayerInTeamFromGame">Remove From Game</li>';
					menuHTML += '<li class="separator"></li>';

					for (let j = 0; j < teamList.length; j++) {
						if (j !== teamIndex) {
							menuHTML += '<li id="changePlayerInTeamToTeam' + j + '" class="menuItem">';
							menuHTML += 'Put in ' + teamList[j];
							menuHTML += '</li>';
						}
					}
					menuHTML += '<li id="moveUp" class="menuItem">Move up</li>';
					menuHTML += '<li id="moveDown" class="menuItem">Move down</li>';
					menuHTML += '<li id="makePlayerNextInTeam" class="menuItem">Make this player next in ' + teamList[teamIndex] + '</li>';
					menuHTML += '</ul>';

					document.getElementById("playerInTeamContextMenuDiv").innerHTML = menuHTML;


					for (let j = 0; j < teamList.length; j++) {
						let changePlayerToTeamLiElement = document.getElementById("changePlayerInTeamToTeam" + j);
						if (changePlayerToTeamLiElement != null) {
							changePlayerToTeamLiElement.addEventListener("click", event => {
								putInTeam(playerIDOfPlayerInTeam, j);
							});
						}
					}

					document.getElementById("removePlayerInTeamFromGame").addEventListener("click", event => {
						removeFromGame(playerIDOfPlayerInTeam);
					});

					document.getElementById("moveUp").addEventListener("click", event => {
						moveInTeam(playerIDOfPlayerInTeam, false);
					});

					document.getElementById("moveDown").addEventListener("click", event => {
						moveInTeam(playerIDOfPlayerInTeam, true);
					});

					document.getElementById("makePlayerNextInTeam").addEventListener("click", event => {
						makePlayerNextInTeam(playerIDOfPlayerInTeam);
					});


					let contextMenu = document.getElementById("playerInTeamContextMenu");
					contextMenu.style.display = 'block';
					contextMenu.style.left = (event.pageX - 10) + "px";
					contextMenu.style.top = (event.pageY - 10) + "px";

					contextMenu.addEventListener("mouseleave", event => {
						hideAllContextMenus();
					})

				});
			}
		}
	}

	let menuItems = document.querySelectorAll(".menuItem");
	for (let i = 0; i < menuItems.length; i++) {
		menuItems[i].addEventListener("click", event => {
			hideAllContextMenus();
		});
	}

	const playerName = myDecode(gameStateObject.yourName);
	const playerTeamIndex = gameStateObject.yourTeamIndex;
	const nextTeamIndex = gameStateObject.nextTeamIndex;

	let htmlParams = '<p>You\'re playing as ' + playerName;
	if (playerTeamIndex >= 0
		&& teamList.length > playerTeamIndex) {
		htmlParams += ' on ' + teamList[playerTeamIndex];
	}
	htmlParams += '.</p>';

	if ((gameStatus === 'PLAYING_A_TURN'
		|| gameStatus === 'READY_TO_START_NEXT_TURN'
		|| gameStatus === 'READY_TO_START_NEXT_ROUND')
		&& typeof (nextTeamIndex) !== 'undefined'
		&& typeof (gameStateObject.currentPlayerID) !== 'undefined') {
		if (nextTeamIndex === playerTeamIndex) {
			if (myPlayerID !== gameStateObject.currentPlayerID) {
				if (gameStatus === 'PLAYING_A_TURN') {
					htmlParams += '<p>You\'re guessing while ' + myDecode(gameStateObject.currentPlayer.name) + ' plays, <b>pay attention!</b></p>';
				}
				else {
					htmlParams += '<p>On the next turn, you\'ll be guessing while ' + myDecode(gameStateObject.currentPlayer.name) + ' plays. <b>Pay attention!</b></p>';
				}
			}
		}
		else if (playerTeamIndex >= 0) {
			if (gameStatus === 'PLAYING_A_TURN') {
				htmlParams += '<p>' + teamList[nextTeamIndex] + ' is playing now, you\'re not on that team. <b>Don\'t say anything!</b></p>';
			}
			else {
				htmlParams += '<p>' + teamList[nextTeamIndex] + ' will play on the next turn, you\'re not on that team. <b>Don\'t say anything!</b></p>';
			}
		}
	}

	if (iAmHosting) {
		htmlParams += '<p>You\'re the host. Remember, with great power comes great responsibility.</p>';
	}
	else if (gameStateObject.host != null) {
		htmlParams += '<p>' + myDecode(gameStateObject.host.name) + ' is hosting.</p>'
	}

	let numRounds = gameStateObject.rounds;
	if (numRounds > 0) {
		htmlParams += "<h2>Settings</h2>\n" +
			"Rounds: " + numRounds + "<br>\n" +
			"Round duration (sec): " + gameStateObject.duration + "<br>\n<hr>\n";
	}
	document.getElementById("gameParamsDiv").innerHTML = htmlParams;

	let namesAchievedObjectList = gameStateObject.namesAchieved;
	let atLeastOneNonZeroScore = false;
	let scoresHTML = "<h2>Scores</h2>\n";
	scoresHTML += '<div style="display: flex; flex-direction: row;">\n';
	for (let t = 0; t < namesAchievedObjectList.length; t++) {
		let namesAchievedObject = namesAchievedObjectList[t];
		let teamName = namesAchievedObject.name;
		let namesAchievedList = namesAchievedObject.namesAchieved;

		scoresHTML += '<div style="padding-right: 4rem;">\n';
		scoresHTML += "<h3>" + teamName + "</h3>\n";
		let score = namesAchievedList.length;
		if (score > 0) {
			atLeastOneNonZeroScore = true;
		}
		scoresHTML += "Score: " + score + "\n";
		scoresHTML += "<ol>\n";
		for (let j = 0; j < namesAchievedList.length; j++) {
			scoresHTML += "<li>" + myDecode(namesAchievedList[j]) + "</li>\n";
		}
		scoresHTML += "</ol>\n</div>\n";
	}
	scoresHTML += '</div>';


	if (!atLeastOneNonZeroScore) {
		scoresHTML = "";
	}

	document.getElementById("scoresDiv").innerHTML = scoresHTML;

	let totalScoresObjectList = gameStateObject.scores;
	let atLeastOneRoundHasBeenRecorded = false;
	let totalScoresHTML = "";

	let tableHeaders = ["Round"];
	let tableColumns = [[]];

	for (let t = 0; t < totalScoresObjectList.length; t++) {
		let totalScoresObject = totalScoresObjectList[t];
		let teamName = totalScoresObject.name;
		let scoreList = totalScoresObject.scores;

		tableHeaders[t + 1] = teamName;

		let total = 0;
		if (scoreList.length > 0) {
			tableColumns[t + 1] = [];
		}
		for (let j = 0; j < scoreList.length; j++) {
			tableColumns[0][j] = j + 1;
			tableColumns[t + 1][j] = scoreList[j];
			total += parseInt(scoreList[j]);
		}
		if (scoreList.length > 0) {
			tableColumns[0][scoreList.length] = "Total";
			tableColumns[t + 1][scoreList.length] = total;
		}
	}

	if (tableColumns[0].length > 1) {
		totalScoresHTML += "<h2>Total Scores</h2>\n";
		totalScoresHTML += '<table>\n';

		totalScoresHTML += "<tr>\n";
		for (let i = 0; i < tableHeaders.length; i++) {
			totalScoresHTML += '<th>' + tableHeaders[i] + "</th>";
		}
		totalScoresHTML += "</tr>\n";

		for (let row = 0; row < tableColumns[0].length; row++) {
			totalScoresHTML += '<tr>\n';
			for (let col = 0; col < tableColumns.length; col++) {
				let styleString = '>';
				if (row == tableColumns[col].length - 1) {
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

function iAmCurrentPlayer() {
	let currentPlayer = gameStateObject.currentPlayer;

	if (currentPlayer != null
		&& currentPlayer.publicID == gameStateObject.publicIDOfRecipient) {
		return true;
	}
	else {
		return false;
	}
}

function iAmHost() {
	let host = gameStateObject.host;

	if (host != null
		&& host.publicID == gameStateObject.publicIDOfRecipient) {
		return true;
	}
	else {
		return false;
	}
}

function submitGameParams() {
	document.getElementById("gameParamsForm").style.display = 'none';

	const numRounds = document.getElementById('numRoundsField').value;
	const roundDuration = document.getElementById('roundDurationField').value;
	const numNames = document.getElementById('numNamesField').value;

	fetch('gameParams', { method: 'POST', body: `numRounds=${numRounds}&roundDuration=${roundDuration}&numNames=${numNames}` })
		.catch(err => console.error(err));
}

function allocateTeams() {
	document.getElementById("requestNamesButton").style.display = 'block';
	fetch('allocateTeams')
		.catch(err => console.error(err));
}

function askGameIDResponse() {
	document.getElementById("divJoinOrHost").style.display = 'none';
	const enteredGameID = document.getElementById('gameIDField').value;

	fetch('askGameIDResponse', { method: 'POST', body: 'gameID=' + enteredGameID })
		.then(result => result.json())
		.then(result => {
			const gameResponse = result.GameResponse;
			if (gameResponse === 'OK' || gameResponse === 'TestGameCreated') {
				document.getElementById("joinGameForm").style.display = 'none';
				document.getElementById("playGameDiv").style.display = 'block';

				gameID = result.GameID;

				document.getElementById("gameIDDiv").innerHTML = '<hr><h2>Game ID: ' + gameID + '</h2>';
				if (gameResponse === 'OK') {
					updateGameInfo('<p>Waiting for others to join...</p>');


					if (!useSocket) {
						updateGameStateForever(gameID);
					}
				}
			}
			else {
				document.getElementById("gameIDErrorDiv").innerHTML = "Unknown Game ID";
			}

		})
		.catch(err => console.error(err));

	// prevent form from being submitted using default mechanism
	return false;
}

function requestNames() {
	document.getElementById("requestNamesButton").style.display = 'none';
	document.getElementById("allocateTeamsDiv").style.display = 'none';
	hideHostDutiesElements();
	fetch('sendNameRequest')
		.catch(err => console.error(err));
}

function setGameStatus(newStatus) {
	if (gameStatus != newStatus) {
		console.log("Changing status from " + gameStatus + " to " + newStatus);
	}

	if (gameStatus != "WAITING_FOR_NAMES"
		&& newStatus == "WAITING_FOR_NAMES") {
		updateGameInfo("Put your names into the hat!");
		addNameRequestForm();
	}

	if (gameStatus != "PLAYING_A_TURN"
		&& newStatus == "PLAYING_A_TURN") {
		currentNameIndex = gameStateObject.currentNameIndex;
		previousNameIndex = gameStateObject.previousNameIndex;

		if (iAmPlaying) {
			document.getElementById("turnControlsDiv").style.display = 'flex';
		}
		updateCurrentNameDiv();
	}

	if (gameStatus != "READY_TO_START_NEXT_TURN"
		&& newStatus == "READY_TO_START_NEXT_TURN") {
		document.getElementById("turnControlsDiv").style.display = 'none';
		document.getElementById("currentNameDiv").innerHTML = "";
		let roundIndex = gameStateObject.roundIndex;
		if (roundIndex != null) {
			roundIndex = parseInt(roundIndex) + 1;
		}
		else {
			roundIndex = "??";
		}
		updateGameInfo("Playing round " + roundIndex + " of " + gameStateObject.rounds);
	}

	if (newStatus == "READY_TO_START_NEXT_ROUND") {
		document.getElementById("gameStatusDiv").innerHTML = "Finished Round! See scores below";
		showHostDutiesElements();
		document.getElementById("startNextRoundButton").style.display = 'block';
	}
	else {
		document.getElementById("startNextRoundButton").style.display = 'none';
	}

	if (newStatus != "READY_TO_START_NEXT_ROUND"
		&& gameStatus == "READY_TO_START_NEXT_ROUND") {
		hideHostDutiesElements();
	}

	if (newStatus == "ENDED") {
		updateGameInfo("Game Over!");
	}

	gameStatus = newStatus;
}

function addNameRequestForm() {
	let html = '<form id="nameListForm" method="post" onsubmit="return false;">\n';
	for (let i = 1; i <= numNamesPerPlayer; i++) {
		html += '<div class="col-label">\n';
		html += '<label for="name' + i + '">Name ' + i + '</label>\n';
		html += '</div>\n';

		html += '<div class="col-textfield">\n';
		html += '<input id="name' + i + '" name="name' + i + '" type="text">\n';
		html += '</div>\n';
	}

	html += '<div class="clear"></div>';
	html += '<button id="submitNamesButton" onclick="submitNameList()">Put in Hat</button>\n';
	html += '</form>\n';

	document.getElementById("nameList").innerHTML = html;

}

function submitNameList() {
	document.getElementById("nameList").style.display = 'none';

	let requestBody = '';
	for (let i = 1; i <= numNamesPerPlayer; i++) {
		const paramName = 'name' + i;
		const nameToSubmit = document.getElementById(paramName).value;
		if (requestBody.length > 0) {
			requestBody += '&';
		}
		requestBody += paramName;
		requestBody += '=';
		requestBody += nameToSubmit;
	}

	fetch('nameList', { method: 'POST', body: requestBody })
		.catch(err => console.error(err));

}

function hideHostDutiesElements() {
	performHostDutiesElements = document.querySelectorAll(".performHostDutiesClass");
	for (let i = 0; i < performHostDutiesElements.length; i++) {
		performHostDutiesElements[i].style.display = 'none';
	}
}

function showHostDutiesElements() {
	if (iAmHost()) {
		// If we've restored a game from backup, need to show the host controls
		document.getElementById('hostGameDiv').style.display = 'block';
		document.getElementById('gameParamsForm').style.display = 'none';
		document.getElementById('allocateTeamsDiv').style.display = 'none';
	}
	performHostDutiesElements = document.querySelectorAll(".performHostDutiesClass");
	for (let i = 0; i < performHostDutiesElements.length; i++) {
		performHostDutiesElements[i].style.display = 'block';
	}
}

function startGame() {
	document.getElementById("startGameButton").style.display = 'none';
	document.getElementById("gameStatusDiv").innerHTML = "";
	fetch('startGame')
		.catch(err => console.error(err));
}

function startTurn() {
	document.getElementById("startTurnButton").style.display = 'none';
	fetch('startTurn')
		.catch(err => console.error(err));
}

function updateCurrentNameDiv() {
	if (iAmPlaying) {
		currentName = myDecode(nameList[currentNameIndex]);
		document.getElementById("currentNameDiv").innerHTML = "Name: " + currentName;
	}
	else {
		document.getElementById("currentNameDiv").innerHTML = "";
	}
}

function gotName() {
	//    gameStateLogging = true;
	currentNameIndex++;
	fetch('setCurrentNameIndex', { method: 'POST', body: 'newNameIndex=' + currentNameIndex })
		.catch(err => console.error(err));


	if (currentNameIndex < nameList.length) {
		updateCurrentNameDiv();
	}
	else {
		document.getElementById("turnControlsDiv").style.display = 'none';
		finishRound();
	}
}

function finishRound() {
	document.getElementById("gameStatusDiv").innerHTML = "Finished Round!";
	document.getElementById("currentNameDiv").innerHTML = "";
}

function startNextRound() {
	fetch('startNextRound')
		.catch(err => console.error(err));

}

function pass() {
	document.getElementById("passButton").disabled = true;
	fetch('pass', { method: 'POST', body: 'passNameIndex=' + currentNameIndex })
		.then(result => result.json())
		.then(result => {
			document.getElementById("passButton").disabled = false;
			const nameListString = result.nameList;
			if (nameListString != null) {
				nameList = nameListString.split(",");
				updateCurrentNameDiv();
			}

		})
		.catch(err => console.error(err));

}

function endTurn() {
	fetch('endTurn')
		.catch(err => console.error(err));

}
function hideAllContextMenus() {
	let contextMenus = document.querySelectorAll(".contextMenuClass");
	for (let i = 0; i < contextMenus.length; i++) {
		contextMenus[i].style.display = 'none';
	}
}

function putInTeam(playerID, teamIndex) {
	fetch('putInTeam', { method: 'POST', body: 'playerID=' + playerID + '&teamIndex=' + teamIndex })
		.catch(err => console.error(err));
}

function removeFromGame(playerID) {
	fetch('removeFromGame', { method: 'POST', body: 'playerID=' + playerID })
		.catch(err => console.error(err));

}

function moveInTeam(playerID, moveDownOrLater) {
	const apiCall = moveDownOrLater ? "moveLater" : "moveEarlier";
	fetch(apiCall, { method: 'POST', body: 'playerID=' + playerID })
		.catch(err => console.error(err));

}

function makePlayerNextInTeam(playerID) {
	fetch('makeNextInTeam', { method: 'POST', body: 'playerID=' + playerID })
		.catch(err => console.error(err));
}

function updateCountdownClock(secondsRemaining) {
	document.getElementById("gameStatusDiv").innerHTML = "Seconds remaining: " + secondsRemaining;
}
