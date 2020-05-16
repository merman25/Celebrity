let webSocket = null;
let firstSocketMessage = true;

let serverGameState = {};

const myGameState = {
	myName: null,
	willHost: false,
	willJoin: false,
	gameParamsSubmitted: false,
	namesRequested: false,
	namesSubmitted: false,
	statusAtLastUpdate: 'WAITING_FOR_PLAYERS',
	currentNameIndex: 0,
	iAmPlaying: false,
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

if (getCookie('restore') === 'true') {
	tryToOpenSocket();
	showOrHideDOMElements('#nameSubmitted');
	showOrHideDOMElements('#willJoinGame');
	showOrHideDOMElements('#gameIDSubmitted');
	showOrHideDOMElements('#gameIDOKResponseReceived');

}
setDOMElementVisibility(myGameState, serverGameState);

function removeChildren(elementOrID) {
	const element = typeof (elementOrID) === 'string' ? document.getElementById(elementOrID) : elementOrID;
	while (element.firstChild)
		element.removeChild(element.firstChild);
}

function appendChildren(elementOrID, ...children) {
	const element = typeof (elementOrID) === 'string' ? document.getElementById(elementOrID) : elementOrID;

	children.forEach(c => {
		if (c) {
			const child = typeof (c) === 'string' ? document.createElement(c) : c;
			element.appendChild(child);
		}
	});
}

function setChildren(elementOrID, ...children) {
	const element = typeof (elementOrID) === 'string' ? document.getElementById(elementOrID) : elementOrID;
	removeChildren(element);
	appendChildren.apply(this, [element, ...children]);
}

function submitName() {
	const username = document.getElementById('nameField').value;
	sendUsername(username);
	myGameState.myName = username;

	showOrHideDOMElements('#nameSubmitted');
	setDOMElementVisibility(myGameState, serverGameState);

	tryToOpenSocket();
}

function requestGameID() {
	myGameState.willJoin = true;
	showOrHideDOMElements('#willJoinGame');
	setDOMElementVisibility(myGameState, serverGameState);
}

async function hostNewGame() {
	myGameState.willHost = true;
	showOrHideDOMElements('#willHostGame');
	setDOMElementVisibility(myGameState, serverGameState);

	try {
		const resultObject = await sendIWillHost();
		updateGameInfo('Waiting for others to join...');
	}
	catch (err) { console.error(err); }
}

function tryToOpenSocket() {
	try {
		const currentURL = window.location.href;
		const currentHostName = currentURL.replace(/^[a-zA-Z]+:\/\//, '')
			.replace(/:[0-9]+.*$/, '');
		if (webSocket)
			webSocket.close();

		webSocket = new WebSocket(`ws://${currentHostName}:8001/`);
		webSocket.onerror = evt => {
			console.error('Error in websocket');
			console.error(evt);
		};
		webSocket.onclose = evt => {
			console.log('websocket closed');
			console.log(evt);
		};
		webSocket.onopen = event => {
			console.log('websocket opened');
			if (getCookie('restore') !== 'true')
				webSocket.send('initial-test');
		};

		webSocket.onmessage = evt => {
			const message = evt.data;
			if (firstSocketMessage) {
				firstSocketMessage = false;
				if (message == 'gotcha') {
					console.log('websocket is providing data');
				}
			}

			if (message.indexOf('GameState=') === 0) {
				console.log('received game state');
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
			else {
				console.log(`message: ${evt.data}`);
			}
		};
	} catch (err) {
		console.error(err);
	}

}

function updateGameInfo(text) {
	document.getElementById('gameInfoDiv').textContent = text;
}

function processGameStateObject(newGameStateObject) {
	serverGameState = newGameStateObject;

	myGameState.myPlayerID = serverGameState.publicIDOfRecipient;
	myGameState.iAmPlaying = iAmCurrentPlayer();
	myGameState.iAmHosting = iAmHost();
	myGameState.myName = serverGameState.yourName;

	if (!myGameState.iAmPlaying) {
		clearTestTrigger();
	}

	const gameIDHeading = createDOMElement('h2', `Game ID: ${serverGameState.gameID}`);
	setChildren('gameIDDiv', 'hr', gameIDHeading);


	updateDOMForReadyToStartNextTurn(myGameState, serverGameState);
	setGameStatus(serverGameState.status);

	if (serverGameState.status == 'PLAYING_A_TURN') {
		updateCountdownClock(serverGameState.secondsRemaining);
	}
	updateTeamlessPlayerList(myGameState, serverGameState);
	updateDOMForWaitingForNames(myGameState, serverGameState);
	updateTeamTable(myGameState, serverGameState);
	updateCurrentPlayerInfo(myGameState, serverGameState);
	updateScoresForRound(serverGameState);
	updateTotalScores(serverGameState);

	const testBotInfo = {
		gameStatus: serverGameState.status,
		gameParamsSet: serverGameState.numNames != null && serverGameState.numNames > 0,
		teamsAllocated: myGameState.teamsAllocated,
		turnCount: serverGameState.turnCount,
	};

	setDOMElementVisibility(myGameState, serverGameState);

	if (serverGameState.roundIndex)
		testBotInfo.roundIndex = serverGameState.roundIndex;
	setTestBotInfo(testBotInfo);
}

function updateDOMForReadyToStartNextTurn(myGameState, serverGameState) {
	const readyToStartNextTurn = serverGameState.status == 'READY_TO_START_NEXT_TURN';
	showOrHideDOMElements('#myTurnNow', readyToStartNextTurn && myGameState.iAmPlaying);
	setDOMElementVisibility(myGameState, serverGameState);

	if (readyToStartNextTurn) {
		if (myGameState.iAmPlaying) {
			addTestTrigger('bot-start-turn');
			document.getElementById('gameStatusDiv').textContent = 'It\'s your turn!';
		}
		else {

			const currentPlayer = serverGameState.currentPlayer;
			if (currentPlayer != null) {
				let currentPlayerName = currentPlayer.name;

				document.getElementById('gameStatusDiv').textContent = `Waiting for ${currentPlayerName} to start turn`;
			}
			else {
				document.getElementById('gameStatusDiv').textContent = 'I don\'t know whose turn it is any more :(';
			}
		}
	}
}

function updateTeamlessPlayerList(myGameState, serverGameState) {
	const playerList = serverGameState.players;

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
	const teamlessPlayerLiElements = document.querySelectorAll('.teamlessPlayerLiClass');
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
		const numPlayersToWaitFor = serverGameState.numPlayersToWaitFor;
		if (numPlayersToWaitFor != null) {
			document.getElementById('gameStatusDiv').textContent = `Waiting for names from ${numPlayersToWaitFor} player(s)`;
		}
		else {
			removeChildren('gameStatusDiv');
		}

		if (numPlayersToWaitFor == null
			|| numPlayersToWaitFor == '0') {
			showOrHideDOMElements('#readyToStartGame');
			setDOMElementVisibility(myGameState, serverGameState);

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
				singleColumn.push(player.name);
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

		const header = createDOMElement('h2', 'Teams');
		const table = createTableByColumn(true, tableColumns, attributesByColumn);

		setChildren('teamList', header, table);

		if (myGameState.iAmHosting) {
			addPlayerInTeamContextMenu();
		}
	}
}

function addPlayerInTeamContextMenu() {
	const playerInTeamTDElements = document.querySelectorAll('.playerInTeamTDClass');
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

function setStyle(element, style) {
	if (style) {
		Object.keys(style).forEach(attr => element.style[attr] = style[attr]);
	}
}


function createDOMElement(elementType, textContent = '', attributes = null, style = null) {
	const element = document.createElement(elementType);
	setAttributes(element, attributes);
	setStyle(element, style);
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
		const stillHaveRowsInAtLeastOneColumn = tableColumns.find(column => row < column.length);

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
	const playerName = serverGameState.yourName;
	const playerTeamIndex = serverGameState.yourTeamIndex;
	const nextTeamIndex = serverGameState.nextTeamIndex;

	let reminderParagraph = null;
	if ((serverGameState.status === 'PLAYING_A_TURN'
		|| serverGameState.status === 'READY_TO_START_NEXT_TURN'
		|| serverGameState.status === 'READY_TO_START_NEXT_ROUND')
		&& typeof (nextTeamIndex) !== 'undefined'
		&& typeof (serverGameState.currentPlayerID) !== 'undefined') {
		if (nextTeamIndex === playerTeamIndex) {
			if (myGameState.myPlayerID !== serverGameState.currentPlayerID) {
				if (serverGameState.status === 'PLAYING_A_TURN') {
					reminderParagraph = createDOMElement('p', `You\'re guessing while ${serverGameState.currentPlayer.name} plays, `);
					reminderParagraph.appendChild(createDOMElement('b', 'pay attention!'));
				}
				else {
					reminderParagraph = createDOMElement('p', `On the next turn, you\'ll be guessing while ${serverGameState.currentPlayer.name} plays. `);
					reminderParagraph.appendChild(createDOMElement('b', 'Pay attention!'));
				}
			}
		}
		else if (playerTeamIndex >= 0) {
			if (serverGameState.status === 'PLAYING_A_TURN') {
				reminderParagraph = createDOMElement('p', `${serverGameState.teams[nextTeamIndex].name} is playing now, you\'re not on that team. `);
				reminderParagraph.appendChild(createDOMElement('b', 'Don\'t say anything!'));
			}
			else {
				reminderParagraph = createDOMElement('p', `${serverGameState.teams[nextTeamIndex].name} will play on the next turn, you\'re not on that team. `);
				reminderParagraph.appendChild(createDOMElement('b', 'Don\'t say anything!'));
			}
		}
	}

	const teamString = (playerTeamIndex >= 0 && serverGameState.teams.length > playerTeamIndex) ? `on ${serverGameState.teams[playerTeamIndex].name}` : '';
	setChildren('gameParamsDiv',
		createDOMElement('p', `You\'re playing as ${playerName} ${teamString}`),
		reminderParagraph,
		createDOMElement('p', myGameState.iAmHosting ? 'You\'re the host. Remember, with great power comes great responsibility.' : `${serverGameState.host.name} is hosting.`),
	);
	if (serverGameState.rounds > 0) {
		appendChildren('gameParamsDiv',
			createDOMElement('h2', 'Settings'),
			createDOMElement('text', `Rounds: ${serverGameState.rounds}`),
			createDOMElement('br'),
			createDOMElement('text', `Round duration (sec): ${serverGameState.duration}`),
			createDOMElement('br'),
			createDOMElement('hr'),
		);
	}

}

function updateScoresForRound(serverGameState) {
	if (serverGameState.namesAchieved.find(({ namesAchieved }) => namesAchieved.length > 0)) {
		const div = createDOMElement('div', '', null, { display: 'flex', 'flex-direction': 'row' });
		serverGameState.namesAchieved.forEach(({ name, namesAchieved }, teamIndex) => {
			const subDiv = createDOMElement('div', '', null, { 'padding-right': '4rem' });
			appendChildren(subDiv,
				createDOMElement('h3', name),
				createDOMElement('text', `Score: ${namesAchieved.length}`),
			);
			const ol = createDOMElement('ol');
			subDiv.appendChild(ol);
			namesAchieved.forEach(name => ol.appendChild(createDOMElement('li', name, { classList: ['achievedNameLi', `team${teamIndex}`] })));
			div.appendChild(subDiv);
		});

		setChildren('scoresDiv',
			createDOMElement('h2', 'Scores'),
			div
		);
	}
	else {
		removeChildren('scoresDiv');
	}
}

function updateTotalScores(serverGameState) {
	const tableColumns = [['Round']];
	const attributesByColumn = [[null]];
	let atLeastOneRoundHasBeenRecorded = false;

	serverGameState.scores.forEach(({ name, scores }, teamIndex) => {
		const singleColumn = [];
		const singleAttributeColumn = [];

		tableColumns.push(singleColumn);
		attributesByColumn.push(singleAttributeColumn);

		singleColumn.push(name);
		singleAttributeColumn.push(null);

		let total = 0;
		scores.forEach((score, row) => {
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
	return serverGameState.currentPlayer
		&& serverGameState.currentPlayer.publicID === serverGameState.publicIDOfRecipient;
}

function iAmHost() {
	return serverGameState.host
		&& serverGameState.host.publicID === serverGameState.publicIDOfRecipient;
}

function submitGameParams() {
	myGameState.gameParamsSubmitted = true;
	showOrHideDOMElements('#gameParamsSubmitted');
	setDOMElementVisibility(myGameState, serverGameState);

	const numRounds = document.getElementById('numRoundsField').value;
	const roundDuration = document.getElementById('roundDurationField').value;
	const numNames = document.getElementById('numNamesField').value;

	sendGameParams(numRounds, roundDuration, numNames);
}

function allocateTeams() {
	showOrHideDOMElements('#teamsAllocated');
	setDOMElementVisibility(myGameState, serverGameState);
	sendAllocateTeamsRequest();
}

async function askGameIDResponse() {
	showOrHideDOMElements('#gameIDSubmitted');
	setDOMElementVisibility(myGameState, serverGameState);
	const enteredGameID = document.getElementById('gameIDField').value;

	try {
		const result = await sendGameIDResponseRequest(enteredGameID);
		const gameResponse = result.GameResponse;
		if (gameResponse === 'OK' || gameResponse === 'TestGameCreated') {
			showOrHideDOMElements('#gameIDOKResponseReceived');
			setDOMElementVisibility(myGameState, serverGameState);

			if (gameResponse === 'OK') {
				updateGameInfo('Waiting for others to join...');
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
	myGameState.namesRequested = true;
	showOrHideDOMElements('#namesRequested');
	showOrHideDOMElements('#showingHostDuties', false);
	setDOMElementVisibility(myGameState, serverGameState);
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
		setDOMElementVisibility(myGameState, serverGameState);
		removeChildren('currentNameDiv');
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
		setDOMElementVisibility(myGameState, serverGameState);

		addTestTrigger('bot-ready-to-start-next-round');
	}
	else {
		showOrHideDOMElements('#readyToStartNextRound', false);
		setDOMElementVisibility(myGameState, serverGameState);
	}

	if (newStatus != 'READY_TO_START_NEXT_ROUND'
		&& myGameState.statusAtLastUpdate == 'READY_TO_START_NEXT_ROUND') {
		showOrHideDOMElements('#showingHostDuties', false);
		setDOMElementVisibility(myGameState, serverGameState);
	}

	if (newStatus == 'ENDED') {
		updateGameInfo('Game Over!');
		addTestTrigger('bot-game-over');
	}

	myGameState.statusAtLastUpdate = newStatus;
}

function addTestTrigger(text) {
	const testTriggerDiv = document.getElementById('testTriggerDiv');
	testTriggerDiv.textContent = text;

	if (!testTriggerDiv.classList.contains('testTriggerClass')) {
		testTriggerDiv.classList.add('testTriggerClass');
	}
}

function clearTestTrigger() {
	const testTriggerDiv = document.getElementById('testTriggerDiv');
	testTriggerDiv.textContent = '';
	testTriggerDiv.classList.remove('testTriggerClass');
}

function addNameRequestForm() {
	const form = createDOMElement('form', '', { id: 'nameListForm' });
	form.onsubmit = () => false;
	for (let i = 1; i <= serverGameState.numNames; i++) {
		const labelDiv = createDOMElement('div', '', { classList: ['col-label'] });
		labelDiv.appendChild(createDOMElement('label', `Name ${i}`, { for: `name${i}` }));

		const inputDiv = createDOMElement('div', '', { classList: ['col-textfield'] });
		inputDiv.appendChild(createDOMElement('input', '', { name: `name${i}`, id: `name${i}`, type: 'text' }));
		appendChildren(form, labelDiv, inputDiv);
	}
	const button = createDOMElement('button', 'Put in Hat', { id: 'submitNamesButton' });
	button.onclick = submitNameList;

	appendChildren(form,
		createDOMElement('div', '', { classList: ['clear'] }),
		button
	);

	setChildren('nameList', form);
}

function submitNameList() {
	myGameState.namesSubmitted = true;

	showOrHideDOMElements('#nameListSubmitted');
	setDOMElementVisibility(myGameState, serverGameState);

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
	setDOMElementVisibility(myGameState, serverGameState);
	removeChildren('gameStatusDiv');
	sendStartGameRequest();
}

function startTurn() {
	showOrHideDOMElements('#turnStarted');
	setDOMElementVisibility(myGameState, serverGameState);
	clearTestTrigger();
	sendStartTurnRequest();
}

function updateCurrentNameDiv() {
	if (myGameState.iAmPlaying) {
		currentName = serverGameState.nameList[myGameState.currentNameIndex];
		document.getElementById('currentNameDiv').textContent = `Name: ${currentName}`;
	}
	else {
		removeChildren('currentNameDiv');
	}
}

function gotName() {
	myGameState.currentNameIndex++;
	sendUpdateCurrentNameIndex(myGameState.currentNameIndex);

	if (myGameState.currentNameIndex < serverGameState.nameList.length) {
		updateCurrentNameDiv();
	}
	else {
		showOrHideDOMElements('#turnEnded');
		setDOMElementVisibility(myGameState, serverGameState);
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
		serverGameState.nameList = result.nameList;
		updateCurrentNameDiv();
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
		testBotInfoDiv.textContent = JSON.stringify(testBotInfo);
	}
	else {
		testBotInfoDiv.textContent = '';
	}
}