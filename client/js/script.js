let webSocket = null;
let useSocket = true;
let firstSocketMessage = true;
let gameStateLogging = false;

let serverGameState = {};

const myGameState = {
	statusAtLastUpdate: 'WAITING_FOR_PLAYERS',
	currentNameIndex: 0,
	iAmPlaying: false,
	gameID: null,
	myPlayerID: null,
	teamsAllocated: false,
};

/* Might be useful later: event when DOM is fully loaded 
document.addEventListener('DOMContentLoaded', () => {} );
*/

document.getElementById('nameSubmitButton').addEventListener('click', submitName);
document.getElementById('join').addEventListener('click', requestGameID);
document.getElementById('gameIDSubmitButton').addEventListener('click', askGameIDResponse);
document.getElementById('host').addEventListener('click', hostNewGame);
document.getElementById('submitGameParamsButton').addEventListener('click', submitGameParams);
document.getElementById('teamsButton').addEventListener('click', allocateTeams);
document.getElementById('requestNamesButton').addEventListener('click', requestNames);
document.getElementById('startGameButton').addEventListener('click', startGame);
document.getElementById('startNextRoundButton').addEventListener('click', startNextRound);
document.getElementById('startTurnButton').addEventListener('click', startTurn);
document.getElementById('gotNameButton').addEventListener('click', gotName);
document.getElementById('passButton').addEventListener('click', pass);
document.getElementById('endTurnButton').addEventListener('click', sendEndTurnRequest);


function htmlEscape(string) {
	return string.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/\"/g, '&quot;')
		.replace(/\'/g, '&#39');
}

function myDecode(string) {
	return htmlEscape(decodeURIComponent(string.replace(/\+/g, ' ')));
}

function removeChildren(elementOrID) {
	const element = typeof (elementOrID) === 'string' ? document.getElementById(elementOrID) : elementOrID;
	while (element.firstChild)
		element.removeChild(element.firstChild);
}

function appendChildren(elementOrID, ...children) {
	const element = typeof (elementOrID) === 'string' ? document.getElementById(elementOrID) : elementOrID;

	children.forEach(c => {
		const child = typeof (c) === 'string' ? document.createElement(c) : c;
		element.appendChild(child);
	});
}

function setChildren(elementOrID, ...children) {
	const element = typeof (elementOrID) === 'string' ? document.getElementById(elementOrID) : elementOrID;
	removeChildren(element);
	appendChildren.apply(this, [element, ...children]);
}

function submitName() {
	showOrHideDOMElements('#nameSubmitted');
	const username = document.getElementById('nameField').value;
	sendUsername(username);

	tryToOpenSocket();
}

function requestGameID() {
	showOrHideDOMElements('#willJoinGame');
}

async function hostNewGame() {
	showOrHideDOMElements('#willHostGame');

	try {
		const resultObject = await sendIWillHost();
		myGameState.gameID = resultObject.gameID;

		const gameIDHeading = document.createElement('h2');
		gameIDHeading.textContent = `Game ID: ${myGameState.gameID}`;
		setChildren('gameIDDiv', 'hr', gameIDHeading);
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
			webSocket = new WebSocket(`ws://${currentHostName}:8001/`);
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
				webSocket.send(`session=${sessionID}`);

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
					console.log(`message: ${evt.data}`);
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
	document.getElementById('gameInfoDiv').innerHTML = html;
}

function updateGameStateForever(gameID) {
	setInterval(updateGameState, 500, gameID);
}

async function updateGameState(gameID) {
	try {
		const result = await retrieveGameStateByHTTP();
		processGameStateObject(result);
	}
	catch (err) { console.error(err) };

}

function processGameStateObject(newGameStateObject) {
	serverGameState = newGameStateObject;

	myGameState.myPlayerID = serverGameState.publicIDOfRecipient;
	myGameState.iAmPlaying = iAmCurrentPlayer();
	myGameState.iAmHosting = iAmHost();

	if (!myGameState.iAmPlaying) {
		clearTestTrigger();
	}

	updateDOMForReadyToStartNextTurn(myGameState, serverGameState);
	setGameStatus(serverGameState.status);

	if (serverGameState.status == 'PLAYING_A_TURN') {
		updateCountdownClock(serverGameState.secondsRemaining);
	}
	updateTeamlessPlayerList(myGameState, serverGameState);
	updateDOMForWaitingForNames(myGameState, serverGameState);
	updateTeamTable(myGameState, serverGameState);
	updateCurrentPlayerInfo(myGameState, serverGameState);
	updateScoresForRound(myGameState, serverGameState);
	updateTotalScores(serverGameState);



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

function updateDOMForReadyToStartNextTurn(myGameState, serverGameState) {
	const readyToStartNextTurn = serverGameState.status == 'READY_TO_START_NEXT_TURN';
	showOrHideDOMElements('#myTurnNow', readyToStartNextTurn && myGameState.iAmPlaying);

	if (readyToStartNextTurn) {
		if (myGameState.iAmPlaying) {
			addTestTrigger('bot-start-turn');
			document.getElementById('gameStatusDiv').textContent = 'It\'s your turn!';
		}
		else {

			const currentPlayer = serverGameState.currentPlayer;
			if (currentPlayer != null) {
				let currentPlayerName = myDecode(currentPlayer.name);

				document.getElementById('gameStatusDiv').textContent = `Waiting for ${currentPlayerName} to start turn`;
			}
			else {
				document.getElementById('gameStatusDiv').textContent = 'I don\'t know whose turn it is any more :(';
			}
		}
	}
}

function updateTeamlessPlayerList(myGameState, serverGameState) {
	let playerList = serverGameState.players;

	if (playerList.length > 0) {
		const heading = createDOMElement('h3', 'Players');
		const ul = document.createElement('ul');

		playerList.forEach(player => {
			const li = createDOMElement('li', '', { classList: ['teamlessPlayerLiClass'] });
			const span = createDOMElement('span', player.name, {
				playerID: player.publicID,
				classList: myGameState.iAmHosting ? ['rightClickable'] : []
			});
			li.appendChild(span);
			ul.appendChild(li);
		});
		setChildren('playerList', heading, ul);

		if (myGameState.iAmHosting
			&& serverGameState.teams.length > 0) {
			addTeamlessPlayerContextMenu();
		}
	}
	else {
		removeChildren('playerList');
	}
}

function addTeamlessPlayerContextMenu() {
	let teamlessPlayerLiElements = document.querySelectorAll('.teamlessPlayerLiClass');
	for (const teamlessPlayerLi of teamlessPlayerLiElements) {

		teamlessPlayerLi.addEventListener('contextmenu', event => {
			const playerID = event.target.getAttribute('playerID');
			event.preventDefault();

			const ul = createDOMElement('ul', '', { id: 'contextMenuForTeamlessPlayer', classList: ['contextMenuClass'] });
			const removePlayerLi = createDOMElement('li', 'Remove From Game', { id: 'removeTeamlessPlayerFromGame', classList: ['menuItem'] });
			removePlayerLi.addEventListener('click', event => {
				sendRemoveFromGameRequest(playerID);
				hideAllContextMenus();
			})
			ul.appendChild(removePlayerLi);

			const separatorLi = createDOMElement('li', '', { classList: ['separator'] });
			ul.appendChild(separatorLi);
			serverGameState.teams.forEach((team, teamIndex) => {
				const changeToTeamLi = createDOMElement('li', `Put in ${team.name}`, { id: `changeToTeam${teamIndex}`, classList: ['menuItem'] });
				changeToTeamLi.addEventListener('click', event => {
					sendPutInTeamRequest(playerID, teamIndex);
					hideAllContextMenus();
				});
				ul.appendChild(changeToTeamLi);
			});

			ul.addEventListener('mouseleave', event => {
				hideAllContextMenus();
			});
			setChildren('teamlessPlayerContextMenuDiv', ul);



			ul.style.left = `${(event.pageX - 10)}px`;
			ul.style.top = `${(event.pageY - 10)}px`;
			ul.style.display = 'block';
		});
	}
}

function updateDOMForWaitingForNames(myGameState, serverGameState) {
	if (serverGameState.status == 'WAITING_FOR_NAMES') {
		let numPlayersToWaitFor = serverGameState.numPlayersToWaitFor;
		if (numPlayersToWaitFor != null) {
			document.getElementById('gameStatusDiv').textContent = `Waiting for names from ${numPlayersToWaitFor} player(s)`;
		}
		else {
			removeChildren('gameStatusDiv');
		}

		if (numPlayersToWaitFor == null
			|| numPlayersToWaitFor == '0') {
			showOrHideDOMElements('#readyToStartGame');

			if (myGameState.iAmHosting) {
				showOrHideDOMElements('#showingHostDutiesElementsWhenIAmHost');
			}
			showOrHideDOMElements('#showingHostDuties');
		}
	}

}

function updateTeamTable(myGameState, serverGameState) {
	if (serverGameState.teams.length > 0) {
		myGameState.teamsAllocated = true;
		const tableColumns = [];
		const attributesByColumn = [];

		serverGameState.teams.forEach((team, col) => {
			const singleColumn = [];
			const singleAttributeColumn = [];

			tableColumns.push(singleColumn);
			attributesByColumn.push(singleAttributeColumn);

			singleColumn.push(team.name);
			singleAttributeColumn.push(null);

			team.playerList.forEach((player, row) => {
				singleColumn.push(myDecode(player.name));
				const attributes = {
					classList: ['playerInTeamTDClass'],
					playerID: player.publicID,
					teamindex: col,
					playerindex: row,
				}
				if (myGameState.iAmHosting)
					attributes.classList.push('rightClickable');

				singleAttributeColumn.push(attributes);
			});
		});

		const header = document.createElement('h2');
		header.textContent = 'Teams';
		const table = createTableByColumn(true, tableColumns, attributesByColumn);

		setChildren('teamList', header, table);

		if (myGameState.iAmHosting) {
			addPlayerInTeamContextMenu();
		}
	}
}

function addPlayerInTeamContextMenu() {
	let playerInTeamTDElements = document.querySelectorAll('.playerInTeamTDClass');
	for (const playerInTeamTD of playerInTeamTDElements) {

		playerInTeamTD.addEventListener('contextmenu', event => {
			event.preventDefault();
			const playerID = event.target.getAttribute('playerID');
			const teamIndex = parseInt(event.target.getAttribute('teamIndex'));

			const ul = createDOMElement('ul', '', { id: 'playerInTeamContextMenu', classList: ['contextMenuClass'] });

			const removePlayerLi = createDOMElement('li', 'Remove From Game', { id: 'removePlayerInTeamFromGame', classList: ['menuItem'] });
			removePlayerLi.addEventListener('click', event => {
				sendRemoveFromGameRequest(playerID);
				hideAllContextMenus();
			});
			ul.appendChild(removePlayerLi);

			const separatorLi = createDOMElement('li', '', { classList: ['separator'] });
			ul.appendChild(separatorLi);

			serverGameState.teams.forEach((team, otherTeamIndex) => {
				if (otherTeamIndex !== teamIndex) {
					const li = createDOMElement('li', `Put in ${team.name}`, { id: `changePlayerInTeamToTeam${otherTeamIndex}`, classList: ['menuItem'] });
					li.addEventListener('click', event => {
						sendPutInTeamRequest(playerID, otherTeamIndex);
						hideAllContextMenus();
					});
					ul.appendChild(li);
				}
			});

			const moveUpLi = createDOMElement('li', 'Move up', { id: 'moveUp', classList: ['menuItem'] });
			const moveDownLi = createDOMElement('li', 'Move down', { id: 'moveDown', classList: ['menuItem'] });
			const makePlayerNextInTeamLi = createDOMElement('li', `Make this player next in ${serverGameState.teams[teamIndex].name}`, { id: 'makePlayerNextInTeam', classList: ['menuItem'] });

			moveUpLi.addEventListener('click', event => {
				sendMoveInTeamRequest(playerID, false);
				hideAllContextMenus();
			});

			moveDownLi.addEventListener('click', event => {
				sendMoveInTeamRequest(playerID, true);
				hideAllContextMenus();
			});
			makePlayerNextInTeamLi.addEventListener('click', event => {
				sendMakePlayerNextInTeamRequest(playerID);
				hideAllContextMenus();
			});

			appendChildren(ul, moveUpLi, moveDownLi, makePlayerNextInTeamLi);

			setChildren('playerInTeamContextMenuDiv', ul);

			ul.style.left = `${(event.pageX - 10)}px`;
			ul.style.top = `${(event.pageY - 10)}px`;
			ul.style.display = 'block';

			ul.addEventListener('mouseleave', event => {
				hideAllContextMenus();
			});
		});
	}
}

function setAttributes(element, attributes) {
	if (attributes) {
		if (attributes.classList)
			attributes.classList.forEach(c => element.classList.add(c));

		Object.keys(attributes).filter(attr => attr !== 'classList')
			.forEach(attr => element.setAttribute(attr, attributes[attr]));
	}
}


function createDOMElement(elementType, textContent = '', attributes = null) {
	const element = document.createElement(elementType);
	setAttributes(element, attributes);
	element.textContent = textContent;
	return element;
}

function createTableByColumn(firstRowIsHeader, tableColumns, attributesByColumn) {
	const table = document.createElement('table');


	if (firstRowIsHeader) {
		const tr = document.createElement('tr');
		tableColumns.forEach((column, colIndex) => {
			const th = createDOMElement('th', column[0], attributesByColumn[colIndex][0]);
			tr.appendChild(th);
		});
		table.appendChild(tr);
	}

	const startIndex = firstRowIsHeader ? 1 : 0;
	for (let row = startIndex; ; row++) {
		let stillHaveRowsInAtLeastOneColumn = tableColumns.find(column => row < column.length);

		if (!stillHaveRowsInAtLeastOneColumn) {
			break;
		}

		const tr = document.createElement('tr');
		tableColumns.forEach((column, colIndex) => {
			const td = document.createElement('td');
			if (row < column.length) {
				setAttributes(td, attributesByColumn[colIndex][row]);
				td.textContent = column[row];
			}
			tr.appendChild(td);
		});

		table.appendChild(tr);
	}

	return table;
}

function updateCurrentPlayerInfo(myGameState, serverGameState) {
	const playerName = myDecode(serverGameState.yourName);
	const playerTeamIndex = serverGameState.yourTeamIndex;
	const nextTeamIndex = serverGameState.nextTeamIndex;

	let htmlParams = `<p>You\'re playing as ${playerName}`;
	if (playerTeamIndex >= 0
		&& serverGameState.teams.length > playerTeamIndex) {
		htmlParams += ` on ${serverGameState.teams[playerTeamIndex].name}`;
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
					htmlParams += `<p>You\'re guessing while ${myDecode(serverGameState.currentPlayer.name)} plays, <b>pay attention!</b></p>`;
				}
				else {
					htmlParams += `<p>On the next turn, you\'ll be guessing while ${myDecode(serverGameState.currentPlayer.name)} plays. <b>Pay attention!</b></p>`;
				}
			}
		}
		else if (playerTeamIndex >= 0) {
			if (serverGameState.status === 'PLAYING_A_TURN') {
				htmlParams += `<p>${serverGameState.teams[nextTeamIndex].name} is playing now, you\'re not on that team. <b>Don\'t say anything!</b></p>`;
			}
			else {
				htmlParams += `<p>${serverGameState.teams[nextTeamIndex].name} will play on the next turn, you\'re not on that team. <b>Don\'t say anything!</b></p>`;
			}
		}
	}

	if (myGameState.iAmHosting) {
		htmlParams += '<p>You\'re the host. Remember, with great power comes great responsibility.</p>';
	}
	else if (serverGameState.host != null) {
		htmlParams += `<p>${myDecode(serverGameState.host.name)} is hosting.</p>`;
	}

	let numRounds = serverGameState.rounds;
	if (numRounds > 0) {
		htmlParams += '<h2>Settings</h2>\n' +
			`Rounds: ${numRounds}<br>\n` +
			`Round duration (sec): ${serverGameState.duration}<br>\n<hr>\n`;
	}
	document.getElementById('gameParamsDiv').innerHTML = htmlParams;
}

function updateScoresForRound(myGameState, serverGameState) {
	let namesAchievedObjectList = serverGameState.namesAchieved;
	let atLeastOneNonZeroScore = false;
	let scoresHTML = '<h2>Scores</h2>\n';
	scoresHTML += '<div style="display: flex; flex-direction: row;">\n';
	for (let t = 0; t < namesAchievedObjectList.length; t++) {
		let namesAchievedObject = namesAchievedObjectList[t];
		let teamName = namesAchievedObject.name;
		let namesAchievedList = namesAchievedObject.namesAchieved;

		scoresHTML += '<div style="padding-right: 4rem;">\n';
		scoresHTML += `<h3>${teamName}</h3>\n`;
		let score = namesAchievedList.length;
		if (score > 0) {
			atLeastOneNonZeroScore = true;
		}
		scoresHTML += `Score: ${score}\n`;
		scoresHTML += '<ol>\n';
		for (let j = 0; j < namesAchievedList.length; j++) {
			scoresHTML += `<li class="achievedNameLi team${t}">${myDecode(namesAchievedList[j])}</li>\n`;
		}
		scoresHTML += '</ol>\n</div>\n';
	}
	scoresHTML += '</div>';


	if (!atLeastOneNonZeroScore) {
		scoresHTML = '';
	}

	document.getElementById('scoresDiv').innerHTML = scoresHTML;
}

function updateTotalScores(serverGameState) {
	const tableColumns = [['Round']];
	const attributesByColumn = [[null]];
	let atLeastOneRoundHasBeenRecorded = false;

	serverGameState.scores.forEach((totalScoresObject, teamIndex) => {
		let teamName = totalScoresObject.name;
		let scoreList = totalScoresObject.scores;
		const singleColumn = [];
		const singleAttributeColumn = [];

		tableColumns.push(singleColumn);
		attributesByColumn.push(singleAttributeColumn);

		singleColumn.push(teamName);
		singleAttributeColumn.push(null);

		let total = 0;
		scoreList.forEach((score, row) => {
			atLeastOneRoundHasBeenRecorded = true;
			if (teamIndex === 0) {
				tableColumns[0].push((row + 1).toString());
				attributesByColumn[0].push('');
			}
			singleColumn.push(score);
			singleAttributeColumn.push({ classList: ['scoreRowClass'] });

			total += parseInt(score);
		});

		if (teamIndex === 0) {
			tableColumns[0].push('Total');
			attributesByColumn[0].push({ classList: ['totalClass'] });
		}
		singleColumn.push(total.toString());
		singleAttributeColumn.push({ classList: ['totalClass'] });
	});

	if (atLeastOneRoundHasBeenRecorded) {
		const header = document.createElement('h2');
		header.textContent = 'Total Scores';
		const table = createTableByColumn(true, tableColumns, attributesByColumn);

		setChildren('totalScoresDiv', header, table);
	}
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

	sendGameParams(numRounds, roundDuration, numNames);
}

function allocateTeams() {
	showOrHideDOMElements('#teamsAllocated');
	sendAllocateTeamsRequest();
}

async function askGameIDResponse() {
	showOrHideDOMElements('#gameIDSubmitted');
	const enteredGameID = document.getElementById('gameIDField').value;

	try {
		const result = await sendGameIDResponseRequest(enteredGameID);
		const gameResponse = result.GameResponse;
		if (gameResponse === 'OK' || gameResponse === 'TestGameCreated') {
			showOrHideDOMElements('#gameIDOKResponseReceived');

			myGameState.gameID = result.GameID;

			const gameIDHeading = document.createElement('h2');
			gameIDHeading.textContent = `Game ID: ${myGameState.gameID}`;
			setChildren('gameIDDiv', 'hr', gameIDHeading);

			if (gameResponse === 'OK') {
				updateGameInfo('<p>Waiting for others to join...</p>');


				if (!useSocket) {
					updateGameStateForever(myGameState.gameID);
				}
			}
		}
		else {
			document.getElementById('gameIDErrorDiv').textContent = 'Unknown Game ID';
			showOrHideDOMElements('#gameIDSubmitted', false);
		}

	}
	catch (err) { console.error(err) };
}

function requestNames() {
	showOrHideDOMElements('#namesRequested');
	showOrHideDOMElements('#showingHostDuties', false);
	sendNameRequest();
}

function setGameStatus(newStatus) {
	if (myGameState.statusAtLastUpdate != 'WAITING_FOR_NAMES'
		&& newStatus == 'WAITING_FOR_NAMES') {
		updateGameInfo('Put your names into the hat!');
		addNameRequestForm();
	}

	if (myGameState.statusAtLastUpdate != 'PLAYING_A_TURN'
		&& newStatus == 'PLAYING_A_TURN') {
		myGameState.currentNameIndex = serverGameState.currentNameIndex;

		if (myGameState.iAmPlaying) {
			document.getElementById('turnControlsDiv').style.display = 'flex';
		}
		updateCurrentNameDiv();
	}

	if (myGameState.statusAtLastUpdate != 'READY_TO_START_NEXT_TURN'
		&& newStatus == 'READY_TO_START_NEXT_TURN') {
		showOrHideDOMElements('#readyToStartNextTurn');
		document.getElementById('currentNameDiv').innerHTML = '';
		let userRoundIndex = serverGameState.roundIndex;
		if (userRoundIndex != null) {
			userRoundIndex = parseInt(userRoundIndex) + 1;
		}
		else {
			userRoundIndex = '??';
		}
		updateGameInfo(`Playing round ${userRoundIndex} of ${serverGameState.rounds}`);
	}

	if (newStatus == 'READY_TO_START_NEXT_ROUND') {
		document.getElementById('gameStatusDiv').textContent = 'Finished Round! See scores below';
		if (iAmHost()) {
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

	if (newStatus != 'READY_TO_START_NEXT_ROUND'
		&& myGameState.statusAtLastUpdate == 'READY_TO_START_NEXT_ROUND') {
		showOrHideDOMElements('#showingHostDuties', false);
	}

	if (newStatus == 'ENDED') {
		updateGameInfo('Game Over!');
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
		html += `<label for="name${i}">Name ${i}</label>\n`;
		html += '</div>\n';

		html += '<div class="col-textfield">\n';
		html += `<input id="name${i}" name="name${i}" type="text">\n`;
		html += '</div>\n';
	}

	html += '<div class="clear"></div>';
	html += '<button id="submitNamesButton" onclick="submitNameList()">Put in Hat</button>\n';
	html += '</form>\n';

	document.getElementById('nameList').innerHTML = html;

}

function submitNameList() {
	showOrHideDOMElements('#nameListSubmitted');

	let nameArr = [];
	for (let i = 1; i <= serverGameState.numNames; i++) {
		const paramName = `name${i}`;
		const nameToSubmit = document.getElementById(paramName).value;
		nameArr.push(nameToSubmit);
	}

	sendNameList(nameArr);
}

function startGame() {
	showOrHideDOMElements('#gameStarted');
	document.getElementById('gameStatusDiv').innerHTML = '';
	sendStartGameRequest();
}

function startTurn() {
	showOrHideDOMElements('#turnStarted');
	clearTestTrigger();
	sendStartTurnRequest();
}

function updateCurrentNameDiv() {
	if (myGameState.iAmPlaying) {
		currentName = myDecode(serverGameState.nameList[myGameState.currentNameIndex]);
		document.getElementById('currentNameDiv').textContent = `Name: ${currentName}`;
	}
	else {
		removeChildren('currentNameDiv');
	}
}

function gotName() {
	//    gameStateLogging = true;
	myGameState.currentNameIndex++;
	sendUpdateCurrentNameIndex(myGameState.currentNameIndex);

	if (myGameState.currentNameIndex < serverGameState.nameList.length) {
		updateCurrentNameDiv();
	}
	else {
		showOrHideDOMElements('#turnEnded');
		finishRound();
	}
}

function finishRound() {
	document.getElementById('gameStatusDiv').textContent = 'Finished Round!';
	removeChildren('currentNameDiv');
}

function startNextRound() {
	clearTestTrigger();
	sendStartNextRoundRequest();
}

async function pass() {
	document.getElementById('passButton').disabled = true;
	try {
		const result = await sendPassRequest(myGameState.currentNameIndex);

		document.getElementById('passButton').disabled = false;
		const nameListString = result.nameList;
		if (nameListString != null) {
			serverGameState.nameList = nameListString.split(',');
			updateCurrentNameDiv();
		}

	}
	catch (err) { console.error(err) };
}

function hideAllContextMenus() {
	let contextMenus = document.querySelectorAll('.contextMenuClass');
	for (let i = 0; i < contextMenus.length; i++) {
		contextMenus[i].style.display = 'none';
	}
}

function updateCountdownClock(secondsRemaining) {
	document.getElementById('gameStatusDiv').textContent = `Seconds remaining: ${secondsRemaining}`;
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