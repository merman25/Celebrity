let webSocket = null;
let useSocket = true;
let firstSocketMessage = true;
let gameStateLogging = false;

let serverGameState = {};

const myGameState = {
	statusAtLastUpdate: "WAITING_FOR_PLAYERS",
	currentNameIndex: 0,
	iAmPlaying: false,
	gameID: null,
	myPlayerID: null,
	teamsAllocated: false,
};

const DOMElementShowHideTriggers = [
	{
		trigger: '#nameSubmitted',
		show: ['#divJoinOrHost'],
		hide: ['#divChooseName']
	},
	{
		trigger: '#willJoinGame',
		show: ['#joinGameForm'],
		hide: ['#join', '#host']
	},
	{
		trigger: '#willHostGame',
		show: ['#hostGameDiv', '#playGameDiv'],
		hide: ['#divJoinOrHost']
	},
	{
		trigger: '#myTurnNow',
		show: ['#startTurnButton'],
		hide: []
	},
	{
		trigger: '#readyToStartGame',
		show: ['#startGameButton'],
		hide: []
	},
	{
		trigger: '#gameParamsSubmitted',
		show: [],
		hide: ['#gameParamsForm']
	},
	{
		trigger: '#teamsAllocated',
		show: ['#requestNamesButton'],
		hide: []
	},
	{
		trigger: '#gameIDSubmitted',
		show: [],
		hide: ['#divJoinOrHost']
	},
	{
		trigger: '#gameIDOKResponseReceived',
		show: ['#playGameDiv'],
		hide: ['#joinGameForm']
	},
	{
		trigger: '#namesRequested',
		show: [],
		hide: ['#requestNamesButton', '#allocateTeamsDiv']
	},
	{
		trigger: '#readyToStartNextTurn',
		show: [],
		hide: ['#turnControlsDiv']
	},
	{
		trigger: '#readyToStartNextRound',
		show: ['#startNextRoundButton'],
		hide: []
	},
	{
		trigger: '#nameListSubmitted',
		show: [],
		hide: ['#nameList']
	},
	{
		trigger: '#showingHostDutiesElementsWhenIAmHost',
		show: ['#hostGameDiv'],
		hide: ['#gameParamsForm', '#allocateTeamsDiv']
	},
	{
		trigger: '#showingHostDuties',
		show: ['.performHostDutiesClass'],
		hide: []
	},
	{
		trigger: '#gameStarted',
		show: [],
		hide: ['#startGameButton']
	},
	{
		trigger: '#turnStarted',
		show: [],
		hide: ['#startTurnButton']
	},
	{
		trigger: '#turnEnded',
		show: [],
		hide: ['#turnControlsDiv']
	},
];

// Function to be used on HTML DOM NodeLists, which are like arrays in some ways, but without
// the usual array functions. They are returned by document.querySelectorAll().
// https://www.w3schools.com/js/js_htmldom_nodelist.asp
const nodeListToArray = function (nodeList) {
	const arr = [];
	nodeList.forEach(node => arr.push(node));
	return arr;
}

const showOrHideDOMElements = function (triggerName, show = true) {
	const positive = show;
	// FP in Javascript isn't the most readable 😂
	DOMElementShowHideTriggers.filter(e => e.trigger === triggerName)
		.forEach(({ show, hide }) => {
			show.map(selector => document.querySelectorAll(selector))
				.map(nodeListToArray)
				.reduce((xs, x) => xs.concat(x), [])
				.forEach(element => element.style.display = positive ? 'block' : 'none');

			hide.map(selector => document.querySelectorAll(selector))
				.map(nodeListToArray)
				.reduce((xs, x) => xs.concat(x), [])
				.forEach(element => element.style.display = positive ? 'none' : 'block');
		});
}



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
	showOrHideDOMElements('#nameSubmitted');

	const username = document.getElementById('nameField').value;

	fetch('username', { method: 'POST', body: 'username=' + username })
		.catch(err => console.error(err));

	tryToOpenSocket();
}

function requestGameID() {
	showOrHideDOMElements('#willJoinGame');
}


async function hostNewGame() {
	showOrHideDOMElements('#willHostGame');

	try {
		const fetchResult = await fetch('hostNewGame');
		const resultObject = await fetchResult.json();
		myGameState.gameID = resultObject.gameID;

		document.getElementById("gameIDDiv").innerHTML = '<hr><h2>Game ID: ' + myGameState.gameID + '</h2>';
		updateGameInfo('<p>Waiting for others to join...</p>');

		if (!useSocket) {
			updateGameStateForever(myGameState.gameID);
		}
	}
	catch (err) { console.error(err); }
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
				if (myGameState.gameID != null) {
					updateGameStateForever(myGameState.gameID);
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

function updateGameStateForever(gameID) {
	setInterval(updateGameState, 500, gameID);
}

async function updateGameState(gameID) {
	try {
		const fetchResult = await fetch('requestGameState', { method: 'POST', body: 'gameID=' + gameID });
		const result = await fetchResult.json();
		processGameStateObject(result);
	}
	catch (err) { console.error(err) };

}

function processGameStateObject(newGameStateObject) {
	serverGameState = newGameStateObject;

	myGameState.myPlayerID = serverGameState.publicIDOfRecipient;
	myGameState.iAmPlaying = iAmCurrentPlayer();
	let iAmHosting = iAmHost();

	if (!myGameState.iAmPlaying) {
		clearTestTrigger();
	}

	if (serverGameState.status == "READY_TO_START_NEXT_TURN") {
		if (myGameState.iAmPlaying) {
			showOrHideDOMElements('#myTurnNow');
			addTestTrigger('bot-start-turn');
			document.getElementById("gameStatusDiv").innerHTML = "It's your turn!";
		}
		else {
			showOrHideDOMElements('#myTurnNow', false);

			const currentPlayer = serverGameState.currentPlayer;
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
		showOrHideDOMElements('#myTurnNow', false);
	}


	setGameStatus(serverGameState.status);

	if (serverGameState.status == "PLAYING_A_TURN") {
		updateCountdownClock(serverGameState.secondsRemaining);
	}


	let playerList = serverGameState.players;

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

		if (iAmHosting
			&& serverGameState.teams.length > 0) {
			let teamlessPlayerLiElements = document.querySelectorAll(".teamlessPlayerLiClass");
			for (let i = 0; i < teamlessPlayerLiElements.length; i++) {
				let teamlessPlayerLi = teamlessPlayerLiElements[i];

				teamlessPlayerLi.addEventListener("contextmenu", event => {
					let playerID = event.target.getAttribute("playerID");
					event.preventDefault();

					let menuHTML = '<ul id="contextMenuForTeamlessPlayer" class="contextMenuClass">';
					menuHTML += '<li class="menuItem" id="removeTeamlessPlayerFromGame">Remove From Game</li>';
					menuHTML += '<li class="separator"></li>';

					for (let j = 0; j < serverGameState.teams.length; j++) {
						menuHTML += '<li id="changeToTeam' + j + '" class="menuItem">';
						menuHTML += 'Put in ' + serverGameState.teams[j].name;
						menuHTML += '</li>';
					}

					menuHTML += '</ul>';

					document.getElementById("teamlessPlayerContextMenuDiv").innerHTML = menuHTML;

					for (let j = 0; j < serverGameState.teams.length; j++) {
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


	if (serverGameState.status == "WAITING_FOR_NAMES") {
		let numPlayersToWaitFor = serverGameState.numPlayersToWaitFor;
		if (numPlayersToWaitFor != null) {
			document.getElementById("gameStatusDiv").innerHTML = "Waiting for names from " + numPlayersToWaitFor + " player(s)";
		}
		else {
			document.getElementById("gameStatusDiv").innerHTML = "";
		}

		if (numPlayersToWaitFor == null
			|| numPlayersToWaitFor == "0") {
			showOrHideDOMElements('#readyToStartGame');

			if (iAmHosting) {
				showOrHideDOMElements('#showingHostDutiesElementsWhenIAmHost');
			}
			showOrHideDOMElements('#showingHostDuties');
		}
	}

	if (serverGameState.teams.length > 0) {
		myGameState.teamsAllocated = true;
		let tableColumns = [];
		let playerIDs = [];

		for (let i = 0; i < serverGameState.teams.length; i++) {
			let teamObject = serverGameState.teams[i];
			let teamName = teamObject.name;

			tableColumns[i] = [];
			playerIDs[i] = [];

			let teamPlayerList = teamObject.playerList;
			for (let j = 0; j < teamPlayerList.length; j++) {
				tableColumns[i][j] = myDecode(teamPlayerList[j].name);
				playerIDs[i][j] = teamPlayerList[j].publicID;
			}
		}

		let htmlTeamList = "";

		htmlTeamList += "<h2>Teams</h2>\n";
		htmlTeamList += '<table>\n';

		htmlTeamList += '<tr>\n';
		for (let i = 0; i < serverGameState.teams.length; i++) {
			htmlTeamList += '<th>';
			htmlTeamList += serverGameState.teams[i].name;
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
					htmlTeamList += ' playerID="' + playerID + '" teamindex="' + col + '" playerindex="' + row + '">';
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

					for (let j = 0; j < serverGameState.teams.length; j++) {
						if (j !== teamIndex) {
							menuHTML += '<li id="changePlayerInTeamToTeam' + j + '" class="menuItem">';
							menuHTML += 'Put in ' + serverGameState.teams[j].name;
							menuHTML += '</li>';
						}
					}
					menuHTML += '<li id="moveUp" class="menuItem">Move up</li>';
					menuHTML += '<li id="moveDown" class="menuItem">Move down</li>';
					menuHTML += '<li id="makePlayerNextInTeam" class="menuItem">Make this player next in ' + serverGameState.teams[teamIndex].name + '</li>';
					menuHTML += '</ul>';

					document.getElementById("playerInTeamContextMenuDiv").innerHTML = menuHTML;


					for (let j = 0; j < serverGameState.teams.length; j++) {
						let changePlayerToTeamLiElement = document.getElementById("changePlayerInTeamToTeam" + j);
						if (changePlayerToTeamLiElement != null) {
							changePlayerToTeamLiElement.addEventListener("click", event => {
								putInTeam(playerIDOfPlayerInTeam, j);
								hideAllContextMenus();
							});
						}
					}

					document.getElementById("removePlayerInTeamFromGame").addEventListener("click", event => {
						removeFromGame(playerIDOfPlayerInTeam);
						hideAllContextMenus();
					});

					document.getElementById("moveUp").addEventListener("click", event => {
						moveInTeam(playerIDOfPlayerInTeam, false);
						hideAllContextMenus();
					});

					document.getElementById("moveDown").addEventListener("click", event => {
						moveInTeam(playerIDOfPlayerInTeam, true);
						hideAllContextMenus();
					});

					document.getElementById("makePlayerNextInTeam").addEventListener("click", event => {
						makePlayerNextInTeam(playerIDOfPlayerInTeam);
						hideAllContextMenus();
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

	const playerName = myDecode(serverGameState.yourName);
	const playerTeamIndex = serverGameState.yourTeamIndex;
	const nextTeamIndex = serverGameState.nextTeamIndex;

	let htmlParams = '<p>You\'re playing as ' + playerName;
	if (playerTeamIndex >= 0
		&& serverGameState.teams.length > playerTeamIndex) {
		htmlParams += ' on ' + serverGameState.teams[playerTeamIndex].name;
	}
	htmlParams += '.</p>';

	if ((serverGameState.status === 'PLAYING_A_TURN'
		|| serverGameState.status === 'READY_TO_START_NEXT_TURN'
		|| serverGameState.status === 'READY_TO_START_NEXT_ROUND')
		&& typeof (nextTeamIndex) !== 'undefined'
		&& typeof (serverGameState.currentPlayerID) !== 'undefined') {
		if (nextTeamIndex === playerTeamIndex) {
			if (myGameState.myPlayerID !== serverGameState.currentPlayerID) {
				if (serverGameState.status === 'PLAYING_A_TURN') {
					htmlParams += '<p>You\'re guessing while ' + myDecode(serverGameState.currentPlayer.name) + ' plays, <b>pay attention!</b></p>';
				}
				else {
					htmlParams += '<p>On the next turn, you\'ll be guessing while ' + myDecode(serverGameState.currentPlayer.name) + ' plays. <b>Pay attention!</b></p>';
				}
			}
		}
		else if (playerTeamIndex >= 0) {
			if (serverGameState.status === 'PLAYING_A_TURN') {
				htmlParams += '<p>' + serverGameState.teams[nextTeamIndex].name + ' is playing now, you\'re not on that team. <b>Don\'t say anything!</b></p>';
			}
			else {
				htmlParams += '<p>' + serverGameState.teams[nextTeamIndex].name + ' will play on the next turn, you\'re not on that team. <b>Don\'t say anything!</b></p>';
			}
		}
	}

	if (iAmHosting) {
		htmlParams += '<p>You\'re the host. Remember, with great power comes great responsibility.</p>';
	}
	else if (serverGameState.host != null) {
		htmlParams += '<p>' + myDecode(serverGameState.host.name) + ' is hosting.</p>'
	}

	let numRounds = serverGameState.rounds;
	if (numRounds > 0) {
		htmlParams += "<h2>Settings</h2>\n" +
			"Rounds: " + numRounds + "<br>\n" +
			"Round duration (sec): " + serverGameState.duration + "<br>\n<hr>\n";
	}
	document.getElementById("gameParamsDiv").innerHTML = htmlParams;

	let namesAchievedObjectList = serverGameState.namesAchieved;
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
			scoresHTML += `<li class="achievedNameLi team${t}">${myDecode(namesAchievedList[j])}</li>\n`;
		}
		scoresHTML += "</ol>\n</div>\n";
	}
	scoresHTML += '</div>';


	if (!atLeastOneNonZeroScore) {
		scoresHTML = "";
	}

	document.getElementById("scoresDiv").innerHTML = scoresHTML;

	let totalScoresObjectList = serverGameState.scores;
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
			totalScoresHTML += '<tr';
			let trStyleString = '>';
			if (row < tableColumns[0].length - 1) {
				trStyleString = ' class="scoreRowClass">';
			}
			totalScoresHTML += trStyleString;

			for (let col = 0; col < tableColumns.length; col++) {
				let tdStyleString = '>';
				if (row == tableColumns[col].length - 1) {
					tdStyleString = ' class="totalClass">';
				}
				totalScoresHTML += '<td' + tdStyleString + tableColumns[col][row] + "</td>";
			}
			totalScoresHTML += "</tr>\n";
		}

		totalScoresHTML += "</table>";
	}

	document.getElementById("totalScoresDiv").innerHTML = totalScoresHTML;

	const testBotInfo = {
		gameStatus: serverGameState.status,
		gameParamsSet: serverGameState.numNames != null && serverGameState.numNames > 0,
		teamsAllocated: myGameState.teamsAllocated,
		turnCount: serverGameState.turnCount,
	};

	if (serverGameState.roundIndex)
		testBotInfo.roundIndex = serverGameState.roundIndex;
	setTestBotInfo(testBotInfo);
}

function iAmCurrentPlayer() {
	let currentPlayer = serverGameState.currentPlayer;

	if (currentPlayer != null
		&& currentPlayer.publicID == serverGameState.publicIDOfRecipient) {
		return true;
	}
	else {
		return false;
	}
}

function iAmHost() {
	let host = serverGameState.host;

	if (host != null
		&& host.publicID == serverGameState.publicIDOfRecipient) {
		return true;
	}
	else {
		return false;
	}
}

function submitGameParams() {
	showOrHideDOMElements('#gameParamsSubmitted');

	const numRounds = document.getElementById('numRoundsField').value;
	const roundDuration = document.getElementById('roundDurationField').value;
	const numNames = document.getElementById('numNamesField').value;

	fetch('gameParams', { method: 'POST', body: `numRounds=${numRounds}&roundDuration=${roundDuration}&numNames=${numNames}` })
		.catch(err => console.error(err));
}

function allocateTeams() {
	showOrHideDOMElements('#teamsAllocated');
	fetch('allocateTeams')
		.catch(err => console.error(err));
}

async function askGameIDResponse() {
	showOrHideDOMElements('#gameIDSubmitted');
	const enteredGameID = document.getElementById('gameIDField').value;

	try {
		const fetchResult = await fetch('askGameIDResponse', { method: 'POST', body: 'gameID=' + enteredGameID });
		const result = await fetchResult.json();
		const gameResponse = result.GameResponse;
		if (gameResponse === 'OK' || gameResponse === 'TestGameCreated') {
			showOrHideDOMElements('#gameIDOKResponseReceived');

			myGameState.gameID = result.GameID;

			document.getElementById("gameIDDiv").innerHTML = '<hr><h2>Game ID: ' + myGameState.gameID + '</h2>';
			if (gameResponse === 'OK') {
				updateGameInfo('<p>Waiting for others to join...</p>');


				if (!useSocket) {
					updateGameStateForever(myGameState.gameID);
				}
			}
		}
		else {
			document.getElementById("gameIDErrorDiv").innerHTML = "Unknown Game ID";
		}

	}
	catch (err) { console.error(err) };
}

function requestNames() {
	showOrHideDOMElements('#namesRequested');
	showOrHideDOMElements('#showingHostDuties', false);
	fetch('sendNameRequest')
		.catch(err => console.error(err));
}

function setGameStatus(newStatus) {
	if (myGameState.statusAtLastUpdate != "WAITING_FOR_NAMES"
		&& newStatus == "WAITING_FOR_NAMES") {
		updateGameInfo("Put your names into the hat!");
		addNameRequestForm();
	}

	if (myGameState.statusAtLastUpdate != "PLAYING_A_TURN"
		&& newStatus == "PLAYING_A_TURN") {
		myGameState.currentNameIndex = serverGameState.currentNameIndex;

		if (myGameState.iAmPlaying) {
			document.getElementById("turnControlsDiv").style.display = 'flex';
		}
		updateCurrentNameDiv();
	}

	if (myGameState.statusAtLastUpdate != "READY_TO_START_NEXT_TURN"
		&& newStatus == "READY_TO_START_NEXT_TURN") {
		showOrHideDOMElements('#readyToStartNextTurn');
		document.getElementById("currentNameDiv").innerHTML = "";
		let userRoundIndex = serverGameState.roundIndex;
		if (userRoundIndex != null) {
			userRoundIndex = parseInt(userRoundIndex) + 1;
		}
		else {
			userRoundIndex = "??";
		}
		updateGameInfo(`Playing round ${userRoundIndex} of ${serverGameState.rounds}`);
	}

	if (newStatus == "READY_TO_START_NEXT_ROUND") {
		document.getElementById("gameStatusDiv").innerHTML = "Finished Round! See scores below";
		if (iAmHosting) {
			// If we've restored a game from backup, need to show the host controls
			showOrHideDOMElements('#showingHostDutiesElementsWhenIAmHost');
		}
		showOrHideDOMElements('#showingHostDuties');
		showOrHideDOMElements('#readyToStartNextRound');

		addTestTrigger('bot-ready-to-start-next-round');
	}
	else {
		showOrHideDOMElements('#readyToStartNextRound', false);
	}

	if (newStatus != "READY_TO_START_NEXT_ROUND"
		&& myGameState.statusAtLastUpdate == "READY_TO_START_NEXT_ROUND") {
		showOrHideDOMElements('#showingHostDuties', false);
	}

	if (newStatus == "ENDED") {
		updateGameInfo("Game Over!");
		addTestTrigger('bot-game-over');
	}

	myGameState.statusAtLastUpdate = newStatus;
}

function addTestTrigger(text) {
	const testTriggerDiv = document.getElementById('testTriggerDiv');
	testTriggerDiv.innerText = text;

	if (!testTriggerDiv.classList.contains('testTriggerClass')) {
		testTriggerDiv.classList.add('testTriggerClass');
	}
}

function clearTestTrigger() {
	const testTriggerDiv = document.getElementById('testTriggerDiv');
	testTriggerDiv.innerText = '';
	testTriggerDiv.classList.remove('testTriggerClass');
}

function addNameRequestForm() {
	let html = '<form id="nameListForm" method="post" onsubmit="return false;">\n';
	for (let i = 1; i <= serverGameState.numNames; i++) {
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
	showOrHideDOMElements('#nameListSubmitted');

	let requestBody = '';
	for (let i = 1; i <= serverGameState.numNames; i++) {
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

function startGame() {
	showOrHideDOMElements('#gameStarted');
	document.getElementById("gameStatusDiv").innerHTML = "";
	fetch('startGame')
		.catch(err => console.error(err));
}

function startTurn() {
	showOrHideDOMElements('#turnStarted');
	clearTestTrigger();
	fetch('startTurn')
		.catch(err => console.error(err));
}

function updateCurrentNameDiv() {
	if (myGameState.iAmPlaying) {
		currentName = myDecode(serverGameState.nameList[myGameState.currentNameIndex]);
		document.getElementById("currentNameDiv").innerHTML = "Name: " + currentName;
	}
	else {
		document.getElementById("currentNameDiv").innerHTML = "";
	}
}

function gotName() {
	//    gameStateLogging = true;
	myGameState.currentNameIndex++;
	fetch('setCurrentNameIndex', { method: 'POST', body: 'newNameIndex=' + myGameState.currentNameIndex })
		.catch(err => console.error(err));


	if (myGameState.currentNameIndex < serverGameState.nameList.length) {
		updateCurrentNameDiv();
	}
	else {
		showOrHideDOMElements('#turnEnded');

		finishRound();
	}
}

function finishRound() {
	document.getElementById("gameStatusDiv").innerHTML = "Finished Round!";
	document.getElementById("currentNameDiv").innerHTML = "";
}

function startNextRound() {
	clearTestTrigger();
	fetch('startNextRound')
		.catch(err => console.error(err));

}

async function pass() {
	document.getElementById("passButton").disabled = true;
	try {
		const fetchResult = await fetch('pass', { method: 'POST', body: 'passNameIndex=' + myGameState.currentNameIndex });
		const result = await fetchResult.json();

		document.getElementById("passButton").disabled = false;
		const nameListString = result.nameList;
		if (nameListString != null) {
			serverGameState.nameList = nameListString.split(",");
			updateCurrentNameDiv();
		}

	}
	catch (err) { console.error(err) };
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

function setTestBotInfo(testBotInfo) {
	const testBotInfoDiv = document.getElementById('testBotInfoDiv');
	if (testBotInfo != null) {
		testBotInfoDiv.innerText = JSON.stringify(testBotInfo);
	}
	else {
		testBotInfoDiv.innerText = '';
	}
}