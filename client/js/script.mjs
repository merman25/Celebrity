import * as util from './util.mjs';
import * as api from './api-calls.mjs';
import * as domManipulation from './dom-manipulation.mjs';

let webSocket = null;
let firstSocketMessage = true;
let gameEventLogging = false;

let serverGameState = {};

const iconSize = '1.5rem';

const myGameState = {
	myName: null,
	willHost: false,
	willJoin: false,
	gameParamsSubmitted: false,
	namesRequested: false,
	statusAtLastUpdate: 'WAITING_FOR_PLAYERS',
	currentNameIndex: 0,
	iAmPlaying: false,
	myPlayerID: null,
	teamsAllocated: false,
	mySubmittedNameList: null,

	sentStartTurn: false,
	sentStartRound: false,
	sentStartGame: false,

	editingSettings: false,
};

window.hackExport = {
	setGameEventLogging: (trueFalse) => gameEventLogging = trueFalse,
	webSocket: webSocket,
	api: api,
};

window.addEventListener('click', hideAllContextMenus);

/* Might be useful later: event when DOM is fully loaded 
document.addEventListener('DOMContentLoaded', () => {} );
*/

/* Function to add a standard click listener to buttons which result in
*  a request to the server.
*/
const addServerRequestClickListener = function(
	element,
	serverRequestFunction,
	serverRequestArgumentRetriever = null,
	myGameStateMutator = null,
	myGameStateReverter = null,
	inputValidator = null,
	responseProcessor = null) {
	element.addEventListener('click', async () => {
		clearNotification();

		let inputArguments = [];
		if (serverRequestArgumentRetriever) {
			inputArguments = serverRequestArgumentRetriever.apply(null);
			if (!Array.isArray(inputArguments)) {
				inputArguments = [inputArguments];
			}
		}

		if (!inputValidator
			|| inputValidator.apply(null, inputArguments)) {
			element.disabled = true;
			if (element.nodeName === 'LI') {
				element.classList.add('disabledMenuItem')
			}

			try {
				restoreWebsocketIfNecessary();
				const response = await serverRequestFunction.apply(null, inputArguments);

				if (myGameStateMutator) {
					myGameStateMutator.apply(null, [inputArguments, myGameState]);
					domManipulation.setDOMElementVisibility(myGameState, serverGameState);
				}

				if (myGameStateReverter) {
					myGameStateReverter.apply(null, [myGameState]);
				}

				if (responseProcessor) {
					responseProcessor.apply(null, [response]);
				}
			}
			catch (err) { console.error(err); }
			finally {
				element.disabled = false;
				if (element.nodeName === 'LI') {
					element.classList.remove('disabledMenuItem')
				}
			}
		}
	});
};

addServerRequestClickListener(
	document.getElementById('nameSubmitButton'),
	api.sendUsername,
	() => document.getElementById('nameField').value,
	([username], myGameState) => myGameState.myName = username,
	null,
	(username) => {
		if (!username
			|| username.trim() === '') {
			alert('Please enter a name');
			return false;
		}
		const usernameLooksLikeGameID = /^[ 0-9]+$/.test(username);
		if (usernameLooksLikeGameID) {
			alert('Names that look like game IDs are not allowed');
			return false;
		}

		return true;
	},
	() => tryToOpenSocket(true)
);


document.getElementById('join').addEventListener('click', () => {
	myGameState.willJoin = true;
	domManipulation.setDOMElementVisibility(myGameState, serverGameState);
});

addServerRequestClickListener(
	document.getElementById('gameIDSubmitButton'),
	api.sendGameIDResponseRequest,
	() => document.getElementById('gameIDField').value.trim(),
	null,
	null,
	(gameID) => {
		if (!gameID
			|| (! /^[0-9]{4}$/.test(gameID)
				&& ! /^test.*/i.test(gameID))) {
			alert('Please enter the 4-digit game ID provided by your host');
			return false;
		}
		else {
			return true;
		}
	},
	(response) => {
		const gameResponse = response.GameResponse;
		if (gameResponse === 'OK' || gameResponse === 'TestGameCreated') {
			// TODO check why this changes any visibility. It seems to, but where do we change either of the state objects?
			domManipulation.setDOMElementVisibility(myGameState, serverGameState);
		}
		else {
			alert('Unknown Game ID');
		}
	}
);

addServerRequestClickListener(
	document.getElementById('host'),
	api.sendIWillHost,
	null,
	(_, myGameState) => myGameState.willHost = true
);

addServerRequestClickListener(
	document.getElementById('submitGameParamsButton'),
	api.sendGameParams,
	() => [document.getElementById('numRoundsField').value,
			document.getElementById('roundDurationField').value,
			document.getElementById('numNamesField').value],
	(_, myGameState) => myGameState.gameParamsSubmitted = true,
	null,
	(numRounds, roundDuration, numNames) => {
		if (numRounds <= 0 || numRounds > 10) {
			alert('Number of rounds must be 1-10');
			return false;
		}
		else if (roundDuration <= 0 || roundDuration > 600) {
			alert('Round duration should be 1-600 seconds');
			return false;
		}
		else if (numNames <= 0 || numNames > 10) {
			alert('Number of names per player must be 1-10');
			return false;
		}
		else {
			return true;
		}
	}
);

addServerRequestClickListener(
	document.getElementById('teamsButton'),
	api.sendAllocateTeamsRequest
);

addServerRequestClickListener(
	document.getElementById('requestNamesButton'),
	api.sendNameRequest,
	null,
	(_, myGameState) => myGameState.namesRequested = true
);

addServerRequestClickListener(
	document.getElementById('startGameButton'),
	api.sendStartGameRequest,
	null,
	(_, myGameState) => myGameState.sentStartGame = true,
	myGameState => myGameState.sentStartGame = false
);

document.getElementById('startNextRoundButton').addEventListener('click', async () => {
	clearTestTrigger();
});

addServerRequestClickListener(
	document.getElementById('startNextRoundButton'),
	api.sendStartNextRoundRequest,
	null,
	(_, myGameState) => myGameState.sentStartRound = true,
	(myGameState) => myGameState.sentStartRound = false
);

document.getElementById('startTurnButton').addEventListener('click', async () => {
	clearTestTrigger();
});

addServerRequestClickListener(
	document.getElementById('startTurnButton'),
	api.sendStartTurnRequest,
	null,
	(_, myGameState) => myGameState.sentStartTurn = true,
	(myGameState) => myGameState.sentStartTurn = false
);

addServerRequestClickListener(
	document.getElementById('gotNameButton'),
	api.sendUpdateCurrentNameIndex,
	() => {
		myGameState.currentNameIndex++;

		if (myGameState.currentNameIndex >= serverGameState.totalNames) {
			domManipulation.setDOMElementVisibility(myGameState, serverGameState);
			finishRound();
		}

		return myGameState.currentNameIndex;
	}
);

addServerRequestClickListener(
	document.getElementById('passButton'),
	api.sendPassRequest,
	() => myGameState.currentNameIndex,
	null,
	null,
	null,
	(result) => {
		serverGameState.currentName = result.currentName;
		updateCurrentNameDiv();
	}
);

addServerRequestClickListener(
	document.getElementById('endTurnButton'),
	api.sendEndTurnRequest
);

addServerRequestClickListener(
	document.getElementById('restoreGameButton'),
	api.sendGetRestorableGameListRequest,
	null, null, null, null,
	(result) => {
		if (! result.gameList
			|| result.gameList.length === 0) {
				alert('No games to restore');
			}
			else {
				const titleH2 = createDOMElement('h2', 'Active games:');
				const activeGameDiv = createDOMElement('div', null, null, {display: 'flex', flexDirection: 'column', maxWidth: '4rem'});

				for (restorableGameID of result.gameList) {
					// Needs to be in a separate variable, otherwise we always send the value from the last iteration of the loop
					const gameIDToSend = restorableGameID;
					const button = createDOMElement('button', restorableGameID, null, {margin: '0.2rem'});

					addServerRequestClickListener(
						button,
						api.sendRestoreGameRequest,
						() => gameIDToSend,
						null, null, null,
						(response) => {
							if (response.result === 'error') {
								alert(response.message);
							}
							else if (response.result === 'still_active') {
								const activePlayers = response.activePlayers;
								const lastSeenAges = response.lastSeenAgesInSeconds;

								// Lots of faffing to ensure the Math.max function is always passed actual numbers as arguments
								let numPlayerIDs = activePlayers ? activePlayers.length : 0;
								if (!numPlayerIDs) {
									numPlayerIDs = 0;
								}
								let numAges = lastSeenAges ? lastSeenAges.length : 0;
								if (!numAges) {
									numAges = 0;
								}
								const numActivePlayers = Math.max(numPlayerIDs, numAges);

								const p1 = 'Looks like some players are still connected. The \'Restore lost game\''
												+ ' function will put you back as the host, but the others need to disconnect first.'
												+ ' Tell them to leave the game, by using the \'Exit Game\' button or just closing their tabs.'

								const p2 = 'If you\'re not the host, you don\'t need to use \'Restore lost game\'.'
												+ ' Just re-join the game using \'Join Existing Game\', and ask the host'
												+ ' to put you back in the right team.'

								setChildren('gameStatusDiv',
									createDOMElement('p', p1),
									createDOMElement('p', p2),
									createDOMElement('span', `${numActivePlayers} active players:`)
								);
								
								const ul = createDOMElement('ul');
								for (let i=0; i<numActivePlayers; i++) {
									let playerName = activePlayers && i < activePlayers.length ? activePlayers[i] : 'UNKNOWN_PLAYER';
									let lastSeenAge = lastSeenAges && i < lastSeenAges.length ? lastSeenAges[i] : 'UNKNOWN';

									appendChildren(ul, createDOMElement('li', `${playerName} (last seen ${lastSeenAge} seconds ago)`));
								}
								appendChildren('gameStatusDiv', ul);

								// Use setTimeout to make the above message appear before the alert does
								setTimeout(() => alert('Some players still active'), 200);
							}
							else if (response.result === 'OK') {
								removeChildren('gameStatusDiv');
								myGameState.willHost = true;
								restoreWebsocketIfNecessary();
							}
							else {
								setCookie('messageOnReload', 'An unknown error occurred');
								location.reload();
							}
						}
					);

					appendChildren(activeGameDiv, button);
				}

				setChildren('gameStatusDiv', titleH2, activeGameDiv);
			}
	}
);

addServerRequestClickListener(
	document.getElementById('changeNameListButton'),
	api.sendRevokeSubmittedNamesRequest,
);

document.getElementById('exitGameButton').addEventListener('click', async () => {
	const answer = confirm('Are you sure you want to exit the game?');
	if (answer) {
		document.getElementById('exitGameButton').disabled = true;
		if (myGameState.myPlayerID && serverGameState.gameID)
			await api.sendRemoveFromGameRequest(myGameState.myPlayerID);

		// reload page
		location.reload();
	}
});

document.getElementById('showInGameSettingsButton').addEventListener('click', () => { 
	myGameState.editingSettings = ! myGameState.editingSettings;
	domManipulation.setDOMElementVisibility(myGameState, serverGameState);
});

addServerRequestClickListener(
	document.getElementById('showNamesCheckBox'),
	api.sendSetCelebrityNameDisplayRequest,
	() => document.getElementById('showNamesCheckBox').checked
);

if (getCookie('restore') === 'true') {
	tryToOpenSocket(false);
}

const messageOnReload = getCookie('messageOnReload');
if (messageOnReload != null
	&& messageOnReload != '') {
	console.log(util.formatTime(), 'messageOnReload', messageOnReload);
	clearCookie('messageOnReload');

	showNotification(messageOnReload);
}

domManipulation.setDOMElementVisibility(myGameState, serverGameState);

// Handle device going to sleep - see https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API
// Set the name of the hidden property and the change event for visibility
let hidden, visibilityChange;
if (typeof document.hidden !== "undefined") { // Opera 12.10 and Firefox 18 and later support 
	hidden = "hidden";
	visibilityChange = "visibilitychange";
} else if (typeof document.msHidden !== "undefined") {
	hidden = "msHidden";
	visibilityChange = "msvisibilitychange";
} else if (typeof document.webkitHidden !== "undefined") {
	hidden = "webkitHidden";
	visibilityChange = "webkitvisibilitychange";
}

document.addEventListener(visibilityChange, () => {
	if (!document[hidden]
		&& webSocket) {
		restoreWebsocketIfNecessary();
	}
});

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

function tryToOpenSocket(isFirstTime) {
	try {
		const currentURL = window.location.href;
		const currentHostName = currentURL.replace(/^[a-zA-Z]+:\/\//, '')
			.replace(/:[0-9]+.*$/, '')
			.replace(/\/$/, '');
		if (webSocket)
			webSocket.close();

		webSocket = new WebSocket(`ws://${currentHostName}:8001/`);
		window.hackExport.webSocket = webSocket;
		webSocket.onerror = evt => {
			console.error('Error in websocket');
			console.error(evt);
		};
		webSocket.onclose = evt => {
			console.log(util.formatTime(), 'websocket closed');
		};
		webSocket.onopen = event => {
			webSocket.send('initial-test');
			if (isFirstTime) {
				logDeviceInfo();
			}
		};

		webSocket.onmessage = evt => {
			const message = evt.data;
			if (firstSocketMessage) {
				firstSocketMessage = false;
				if (message === 'gotcha') {
					console.log(util.formatTime(), 'websocket connection OK');
				}
			}

			if (message.indexOf('JSON=') === 0) {
				const jsonString = message.substring('JSON='.length, message.length);
				const messageObject = JSON.parse(jsonString);
				if (messageObject.isGameState) {
					if (gameEventLogging) {
						const keyValStringArr = [`Type [${messageObject.GameEventType}]`];
						Object.entries(messageObject)
							.filter(entry => entry[0].indexOf('GameEvent') === 0 && entry[0] !== 'GameEventType')
							.map(([key, value]) => `${key.substring('GameEvent'.length)} [${value}]`)
							.forEach(text => keyValStringArr.push(text));
						console.log(util.formatTime(), 'Game event', keyValStringArr.join(', '));
					}
					processGameStateObject(messageObject);
				}
				else if (messageObject.GameEventType === 'TurnTimeRemaining') {
					const millisRemainingString = messageObject.GameEventMillisRemaining;
					const millisRemaining = parseInt(millisRemainingString);
					const secondsRemaining = Math.ceil(millisRemaining / 1000);

					updateCountdownClock(secondsRemaining);
				}
				else {
					console.log(util.formatTime(), 'object received', messageObject);
				}
			}

			else if (message.indexOf('GameState=') === 0) {
				// Happens when we refresh
				const gameStateString = message.substring('GameState='.length, message.length);
				const gameObj = JSON.parse(gameStateString);
				processGameStateObject(gameObj);
			}
			else if (message !== 'gotcha') {
				console.log(util.formatTime(), `message: ${evt.data}`);
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

	/* Set testBotInfo before updating the DOM, because sometimes the test bots
	 * will wait for a DOM update (e.g. the hiding of the startTurn button)
	 * before trying to read the testBotInfo
	*/
	const testBotInfo = {
		gameStatus: serverGameState.status,
		gameParamsSet: serverGameState.numNames != null && serverGameState.numNames > 0,
		teamsAllocated: serverGameState.teams.length > 0,
		turnCount: serverGameState.turnCount,
		gameGlobalNameIndex: serverGameState.gameGlobalNameIndex,
		scores: serverGameState.namesAchieved.map(({name, namesAchieved}) => namesAchieved.length),
	};
	if (serverGameState.roundIndex)
		testBotInfo.roundIndex = serverGameState.roundIndex;
	setTestBotInfo(testBotInfo);

	myGameState.myPlayerID = serverGameState.publicIDOfRecipient;
	myGameState.iAmPlaying = iAmCurrentPlayer();
	myGameState.iAmHosting = iAmHost();
	myGameState.myName = serverGameState.yourName;
	myGameState.mySubmittedNameList = serverGameState.submittedNameList;

	if (serverGameState.testing) {
		gameEventLogging = true;
	}

	if (!myGameState.iAmPlaying) {
		clearTestTrigger();
	}

	if (serverGameState.sessionMaxAge) {
		const sessionCookie = getCookie('session');
		if (sessionCookie != null) {
			setCookie('session', sessionCookie, serverGameState.sessionMaxAge);
		}
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
	updateMySubmittedNameList(myGameState, serverGameState);
	updateTeamTable(myGameState, serverGameState);
	updateCurrentPlayerInfo(myGameState, serverGameState);
	updateScoresForRound(serverGameState);
	updateTotalScores(serverGameState);
	updateNumTeamsChooser(serverGameState);

	document.getElementById('showNamesCheckBox').checked = serverGameState.displayCelebNames;
	domManipulation.setDOMElementVisibility(myGameState, serverGameState);

	applyTheme();

	// Add test trigger last, so that when bots see it, DOM updates have been done.
	setTestTriggerIfNecessary(myGameState, serverGameState);
}

function setTestTriggerIfNecessary(myGameState, serverGameState) {
	if (serverGameState.status === 'READY_TO_START_NEXT_TURN') {
		if (myGameState.iAmPlaying) {
			addTestTrigger('bot-start-turn');
		}
		else {
			addTestTrigger('bot-wait-for-turn');
		}
	}
	else if (serverGameState.status === 'READY_TO_START_NEXT_ROUND') {
		addTestTrigger('bot-ready-to-start-next-round');
	}
	else if (serverGameState.status === 'ENDED') {
		addTestTrigger('bot-game-over');
	}
}

function updateDOMForReadyToStartNextTurn(myGameState, serverGameState) {
	const readyToStartNextTurn = serverGameState.status == 'READY_TO_START_NEXT_TURN';
	domManipulation.setDOMElementVisibility(myGameState, serverGameState);

	if (readyToStartNextTurn) {
		if (myGameState.iAmPlaying) {
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
			let playerName = player.name;
			if (player.emoji) {
				playerName = player.emoji + ' ' + playerName;
			}
			const li = createDOMElement('li', '', { playerID: player.publicID, classList: ['teamlessPlayerLiClass'] });
			const span = createDOMElement('span', playerName, { classList: myGameState.iAmHosting ? ['rightClickable'] : [] });

			if (player.icon) {
				const img = createDOMElement('img', null, {src: player.icon, alt: 'player icon'}, { height: iconSize, paddingRight: '0.25rem'});
				li.appendChild(img);
			}

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
		const playerID = teamlessPlayerLi.getAttribute('playerID');

		const ul = createDOMElement('ul', '', { id: 'contextMenuForTeamlessPlayer', classList: ['contextMenuClass'] });
		const removePlayerLi = createDOMElement('li', 'Remove From Game', { id: 'removeTeamlessPlayerFromGame', classList: ['menuItem'] });
		addServerRequestClickListener(
			removePlayerLi,
			api.sendRemoveFromGameRequest,
			() => playerID,
			null, null, null,
			hideAllContextMenus
		);
		ul.appendChild(removePlayerLi);

		const separatorLi = createDOMElement('li', '', { classList: ['separator'] });
		ul.appendChild(separatorLi);
		serverGameState.teams.forEach((team, teamIndex) => {
			const changeToTeamLi = createDOMElement('li', `Put in ${team.name}`, { id: `changeToTeam${teamIndex}`, classList: ['menuItem'] });
			addServerRequestClickListener(
				changeToTeamLi,
				api.sendPutInTeamRequest,
				() => [playerID, teamIndex],
				null, null, null,
				hideAllContextMenus
			);
			ul.appendChild(changeToTeamLi);
		});

		if (Number(playerID) !== myGameState.myPlayerID) {
			const makePlayerHostLi = createDOMElement('li', 'Make this player the host', { classList: ['menuItem'] });
			const playerName = teamlessPlayerLi.textContent;
			addServerRequestClickListener(
				makePlayerHostLi,
				api.sendMakePlayerHostRequest,
				() => playerID,
				null, null,
				() => confirm(`Are you sure you want to stop hosting and make ${playerName} the host?`)
			);
			ul.appendChild(makePlayerHostLi);
		}

		teamlessPlayerLi.addEventListener('contextmenu', event => {
			event.preventDefault();

			setChildren('teamlessPlayerContextMenuDiv', ul);

			ul.style.left = `${(event.pageX - 10)}px`;
			ul.style.top = `${(event.pageY - 10)}px`;
			ul.style.display = 'block';
			document.getElementById('teamlessPlayerContextMenuDiv').style.display = 'block';
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
			domManipulation.setDOMElementVisibility(myGameState, serverGameState);
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
				let playerName = player.name;
				if (player.emoji) {
					playerName = player.emoji + ' ' + playerName;
				}
					singleColumn.push(playerName);
				const attributes = {
					classList: ['playerInTeamTDClass'],
					playerID: player.publicID,
					teamindex: col,
					playerindex: row,
					icon: player.icon
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
		const playerID = playerInTeamTD.getAttribute('playerID');
		const teamIndex = parseInt(playerInTeamTD.getAttribute('teamIndex'));

		const ul = createDOMElement('ul', '', { id: 'playerInTeamContextMenu', classList: ['contextMenuClass'] });

		const removePlayerLi = createDOMElement('li', 'Remove From Game', { id: 'removePlayerInTeamFromGame', classList: ['menuItem'] });
		addServerRequestClickListener(
			removePlayerLi,
			api.sendRemoveFromGameRequest,
			() => playerID,
			null, null, null,
			hideAllContextMenus
		);
		ul.appendChild(removePlayerLi);

		const separatorLi = createDOMElement('li', '', { classList: ['separator'] });
		ul.appendChild(separatorLi);

		serverGameState.teams.forEach((team, otherTeamIndex) => {
			if (otherTeamIndex !== teamIndex) {
				const li = createDOMElement('li', `Put in ${team.name}`, { id: `changePlayerInTeamToTeam${otherTeamIndex}`, classList: ['menuItem'] });
				addServerRequestClickListener(
					li,
					api.sendPutInTeamRequest,
					() => [playerID, otherTeamIndex],
					null, null, null,
					hideAllContextMenus
				);
				ul.appendChild(li);
			}
		});

		const moveUpLi = createDOMElement('li', 'Move up', { id: 'moveUp', classList: ['menuItem'] });
		const moveDownLi = createDOMElement('li', 'Move down', { id: 'moveDown', classList: ['menuItem'] });
		const makePlayerNextInTeamLi = createDOMElement('li', `Make this player next in ${serverGameState.teams[teamIndex].name}`, { id: 'makePlayerNextInTeam', classList: ['menuItem'] });
		const makeThisTeamNextLi = createDOMElement('li', 'Make this team go next', { id: 'makeTeamGoNext', classList: ['menuItem'] });

		addServerRequestClickListener(
			moveUpLi,
			api.sendMoveInTeamRequest,
			() => [playerID, false],
			null, null, null,
			hideAllContextMenus
		);

		addServerRequestClickListener(
			moveDownLi,
			api.sendMoveInTeamRequest,
			() => [playerID, true],
			null, null, null,
			hideAllContextMenus
		);

		addServerRequestClickListener(
			makePlayerNextInTeamLi,
			api.sendMakePlayerNextInTeamRequest,
			() => playerID,
			null, null, null,
			hideAllContextMenus
		);

		addServerRequestClickListener(
			makeThisTeamNextLi,
			api.sendMakeThisTeamNextRequest,
			() => teamIndex,
			null, null, null,
			hideAllContextMenus
		);

		appendChildren(ul, moveUpLi, moveDownLi, makePlayerNextInTeamLi, makeThisTeamNextLi);

		if (Number(playerID) !== myGameState.myPlayerID) {
			const makePlayerHostLi = createDOMElement('li', 'Make this player the host', { id: 'makePlayerHost', classList: ['menuItem'] });
			const playerName = playerInTeamTD.textContent;
			addServerRequestClickListener(
				makePlayerHostLi,
				api.sendMakePlayerHostRequest,
				() => playerID,
				null, null,
				() => confirm(`Are you sure you want to stop hosting and make ${playerName} the host?`),
				hideAllContextMenus
			);
			ul.appendChild(makePlayerHostLi);
		}


		playerInTeamTD.addEventListener('contextmenu', event => {
			event.preventDefault();
			const playerID = playerInTeamTD.getAttribute('playerID');

			setChildren('playerInTeamContextMenuDiv', ul);

			ul.style.left = `${(event.pageX - 10)}px`;
			ul.style.top = `${(event.pageY - 10)}px`;
			ul.style.display = 'block';
			document.getElementById('playerInTeamContextMenuDiv').style.display = 'block';
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
				if (attributesByColumn[colIndex][row].icon) {
					const span = createDOMElement('span', column[row]);
					setAttributes(span, attributesByColumn[colIndex][row]);
					appendChildren(td,
						createDOMElement('img', null, {src: attributesByColumn[colIndex][row].icon, alt: 'player icon'}, { height: iconSize, paddingRight: '0.25rem'}),
						span
					);
				}
				else {
					setAttributes(td, attributesByColumn[colIndex][row]);
					td.textContent = column[row];
				}
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
			if (serverGameState.displayCelebNames) {
				const ol = createDOMElement('ol');
				subDiv.appendChild(ol);
				namesAchieved.forEach(name => ol.appendChild(createDOMElement('li', name, { classList: ['achievedNameLi', `team${teamIndex}`] })));
			}
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

function updateNumTeamsChooser(serverGameState) {
	removeChildren('numTeamsDropdownList');
	if (serverGameState.players) {
		const possTeamNumbers = util.possibleNumbersOfTeams(serverGameState.players.length);
		for (let teamSize of possTeamNumbers) {
			appendChildren('numTeamsDropdownList', createDOMElement('option', teamSize));
		}
	}
}

function updateMySubmittedNameList(myGameState) {
	removeChildren('mySubmittedNamesOL');
	if (myGameState.mySubmittedNameList) {
		for (let i = 0; i < myGameState.mySubmittedNameList.length; i++) {
			const submittedName = myGameState.mySubmittedNameList[i];
			const li = createDOMElement('li', submittedName);
			document.getElementById('mySubmittedNamesOL').appendChild(li);

			/* Put names back into the text fields - handy if a refresh
			* has cleared the fields, means the user doesn't have to re-type
			* everything if they only want to change one name
			*/
			const textFieldID = `name${i+1}`;
			const textField = document.getElementById(textFieldID);
			if (textField) {
				textField.value = submittedName;
			}
		}
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

function setGameStatus(newStatus) {
	if (newStatus === 'WAITING_FOR_PLAYERS')
		updateGameInfo('Waiting for others to join...')

	if (myGameState.statusAtLastUpdate != 'WAITING_FOR_NAMES'
		&& newStatus == 'WAITING_FOR_NAMES') {
		updateGameInfo('Put your names into the hat!');
		addNameRequestForm();
	}

	if (newStatus == 'PLAYING_A_TURN') {
		myGameState.currentNameIndex = serverGameState.currentNameIndex;

		if (myGameState.iAmPlaying) {
			document.getElementById('turnControlsDiv').style.display = 'flex';
		}
		updateCurrentNameDiv();
	}

	if (myGameState.statusAtLastUpdate != 'READY_TO_START_NEXT_TURN'
		&& newStatus == 'READY_TO_START_NEXT_TURN') {
		domManipulation.setDOMElementVisibility(myGameState, serverGameState);
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
		domManipulation.setDOMElementVisibility(myGameState, serverGameState);
	}
	else {
		domManipulation.setDOMElementVisibility(myGameState, serverGameState);
	}

	if (newStatus != 'READY_TO_START_NEXT_ROUND'
		&& myGameState.statusAtLastUpdate == 'READY_TO_START_NEXT_ROUND') {
		domManipulation.setDOMElementVisibility(myGameState, serverGameState);
	}

	if (newStatus == 'ENDED') {
		updateGameInfo('Game Over!');
	}

	myGameState.statusAtLastUpdate = newStatus;
}

function addTestTrigger(text) {
	if (serverGameState.testing) {
		console.log(util.formatTime(), `adding test trigger ${text}`);
		const testTriggerDiv = document.getElementById('testTriggerDiv');
		testTriggerDiv.textContent = text;

		if (!testTriggerDiv.classList.contains('testTriggerClass')) {
			testTriggerDiv.classList.add('testTriggerClass');
		}
	}
}

function clearTestTrigger() {
	const testTriggerDiv = document.getElementById('testTriggerDiv');
	if (serverGameState.testing
		&& testTriggerDiv.textContent !== '') {
		console.log(util.formatTime(), 'clearing test trigger');
	}
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
	const nameListSubmitButton = createDOMElement('button', 'Put in Hat', { id: 'submitNamesButton' });

	addServerRequestClickListener(
		nameListSubmitButton,
		api.sendNameList,
		() => {
			const nameArr = [];
			for (let i = 1; i <= serverGameState.numNames; i++) {
				const paramName = `name${i}`;
				const nameToSubmit = document.getElementById(paramName).value;
				nameArr.push(nameToSubmit);
			}

			return [nameArr];
		},
		null,
		null,
		(nameArr) => {
			for (name of nameArr) {
				if (name == null
					|| name.trim() === '') {
					alert('Please enter some text for each name');
					return false;
				}
			}
			return true;
		}
	);

	appendChildren(form,
		createDOMElement('div', '', { classList: ['clear'] }),
		nameListSubmitButton
	);

	setChildren('nameList', form);
}

function updateCurrentNameDiv() {
	document.getElementById('gotNameButton').disabled = false;
	if (myGameState.iAmPlaying) {
		let currentName = serverGameState.currentName;
		if (currentName != null)
			document.getElementById('currentNameDiv').textContent = `Name: ${currentName}`;
		else
			document.getElementById('currentNameDiv').textContent = '';
	}
	else {
		removeChildren('currentNameDiv');
	}
}

function finishRound() {
	document.getElementById('gameStatusDiv').textContent = 'Finished Round!';
	removeChildren('currentNameDiv');
}

function hideAllContextMenus() {
	let contextMenus = document.querySelectorAll('.contextMenuClass');
	for (let i = 0; i < contextMenus.length; i++) {
		contextMenus[i].style.display = 'none';
	}

	document.getElementById('teamlessPlayerContextMenuDiv').style.display = 'none';
	document.getElementById('playerInTeamContextMenuDiv').style.display = 'none';
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

export function showNotification(message) {
	const footer = document.getElementById('notificationFooterDiv');
	const closeButton = createDOMElement('span', '\xd7', { classList: ['close'] });
	setChildren(footer,
		createDOMElement('span', message),
		closeButton
	);
	closeButton.addEventListener('click', () => footer.style.display = 'none');
	footer.style.display = 'block';
}

function clearNotification() {
	const footer = document.getElementById('notificationFooterDiv');
	removeChildren(footer);
	footer.style.display = 'none';
}

export function restoreWebsocketIfNecessary() {
	if (( webSocket == null
			|| webSocket.readyState === WebSocket.CLOSED
			|| webSocket.readyState === WebSocket.CLOSING)
		&& getCookie('session')) {
		tryToOpenSocket(false);
	}
}

function log() {
	restoreWebsocketIfNecessary();
	// arguments is not an array, it's an object. Here we make it an array
	const args = Array.prototype.slice.apply(arguments); 
	if (webSocket
		&& webSocket.readyState == WebSocket.OPEN) {
			const message = 'JSON=' + JSON.stringify({log: args});
			webSocket.send(message);
		}
}

const browserBrands = [
	{ name: 'Brave', detector: async () => (navigator.brave && await navigator.brave.isBrave() || false) },
	{ name: "Firefox", marker: "Firefox/" },
	{ name: "Edge", marker: "Edg/" },
	{ name: "Opera", marker: "OPR/" },
	{ name: "Electron", marker: "Electron/" },
	{ name: "Chrome", marker: "Chrome/" },
	{ name: "Safari", marker: "Safari/" },
	{ name: "Internet Explorer", marker: "rv:" },
];

async function detectBrowser() {
	const userAgent = navigator.userAgent;
	const unknown = 'Unknown';
	for (const browser of browserBrands) {
		if (browser.detector) {
			const result = await browser.detector();
			if (result) {
				return { name: browser.name, version: unknown };
			}
		}

		if (browser.marker) {
			const indexOfMarker = userAgent.indexOf(browser.marker);
			if (indexOfMarker > 0) {
				const indexOfEndOfMarkerString = indexOfMarker + browser.marker.length;
				let indexOfEndOfVersionString = userAgent.indexOf(' ', indexOfEndOfMarkerString);
				if (indexOfEndOfVersionString < 0) {
					indexOfEndOfVersionString = userAgent.length;
				}

				const version = userAgent.substring(indexOfEndOfMarkerString, indexOfEndOfVersionString);

				return { name: browser.name, version: version };
			}
		}
	}

	return { name: unknown, version: unknown }
}

async function logDeviceInfo() {
	const browser = await detectBrowser();
	log('Screen width', window.screen.width, 'Screen height', window.screen.height, 'Browser', browser.name, 'Version', browser.version);
}


function applyTheme() {
	const theme = getCookie('theme');
	if (theme) {
		const themePrefix = 'theme_';
		const replaceTheme = element => {
			for (let i=0; i<element.classList.length; i++) {
				const cssClass = element.classList.item(i);
				if (cssClass.startsWith(themePrefix)) {
					element.classList.remove(cssClass);
					i--;
				}
			}
			element.classList.add(themePrefix + theme);
		};
		replaceTheme(document.getElementById('gameTitleH1'));
		replaceTheme(document.getElementById('backgroundColourGradientDiv'));
		replaceTheme(document.querySelector('html'));
		document.querySelectorAll('a').forEach(replaceTheme);
	}
}

applyTheme();