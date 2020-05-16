const selectorsToShowOrHide = [
	{
		selector: '#divJoinOrHost',
		styleFn: (myGameState, serverGameState) => myGameState.myName && !serverGameState.gameID ? 'block' : 'none',
	},
	{
		selector: '#divChooseName',
		styleFn: (myGameState, serverGameState) => !myGameState.myName ? 'block' : 'none',
	},
	{
		selector: '#joinGameForm',
		styleFn: (myGameState, serverGameState) => myGameState.willJoin && !serverGameState.gameID ? 'block' : 'none',
	},
	{
		selector: '#join',
		styleFn: (myGameState, serverGameState) => myGameState.myName && !serverGameState.gameID && !myGameState.willJoin && !myGameState.willHost ? '' : 'none',
	},
	{
		selector: '#host',
		styleFn: (myGameState, serverGameState) => myGameState.myName && !serverGameState.gameID && !myGameState.willJoin && !myGameState.willHost ? '' : 'none',
	},
	{
		selector: '#hostGameDiv',
		styleFn: (myGameState, serverGameState) => myGameState.myName && serverGameState.gameID && myGameState.iAmHosting ? 'block' : 'none',
	},
	{
		selector: '#playGameDiv',
		styleFn: (myGameState, serverGameState) => 'block',
	},
	{
		selector: '#startTurnButton',
		styleFn: (myGameState, serverGameState) => serverGameState.status === 'READY_TO_START_NEXT_TURN' && myGameState.iAmPlaying ? 'block' : 'none',
	},
	{
		selector: '#startGameButton',
		styleFn: (myGameState, serverGameState) => myGameState.iAmHosting && serverGameState.status === 'WAITING_FOR_NAMES' && serverGameState.numPlayersToWaitFor === 0 ? 'block' : 'none',
	},
	{
		selector: '#gameParamsForm',
		styleFn: (myGameState, serverGameState) => myGameState.myName && serverGameState.gameID && myGameState.iAmHosting && !myGameState.gameParamsSubmitted && !serverGameState.rounds ? 'block' : 'none',
	},
	{
		selector: '#requestNamesButton',
		styleFn: (myGameState, serverGameState) =>
			myGameState.myName
				&& serverGameState.gameID
				&& myGameState.iAmHosting
				&& serverGameState.rounds
				&& serverGameState.teams
				&& serverGameState.teams.length > 0
				&& !myGameState.namesRequested
				&& serverGameState.status === 'WAITING_FOR_PLAYERS' ? 'block' : 'none',
	},
	{
		selector: '#allocateTeamsDiv',
		styleFn: (myGameState, serverGameState) =>
			myGameState.myName
				&& serverGameState.gameID
				&& myGameState.iAmHosting
				&& !myGameState.namesRequested
				&& serverGameState.status === 'WAITING_FOR_PLAYERS' ? 'block' : 'none',
	},
	{
		selector: '#turnControlsDiv',
		styleFn: (myGameState, serverGameState) => serverGameState.status === 'PLAYING_A_TURN' && myGameState.iAmPlaying ? 'block' : 'none',
	},
	{
		selector: '#startNextRoundButton',
		styleFn: (myGameState, serverGameState) => myGameState.iAmHosting && serverGameState.status === 'READY_TO_START_NEXT_ROUND' ? 'block' : 'none',
	},
	{
		selector: '#nameList',
		styleFn: (myGameState, serverGameState) => serverGameState.status === 'WAITING_FOR_NAMES' && !myGameState.namesSubmitted ? 'block' : 'none',
	},
	{
		selector: '.performHostDutiesClass',
		styleFn: (myGameState, serverGameState) => 'block',
	},
];

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

const setDOMElementVisibility = function (myGameState, serverGameState) {
	selectorsToShowOrHide.forEach(({ selector, styleFn }) => {
		const display = styleFn(myGameState, serverGameState);
		document.querySelectorAll(selector)
			.forEach(element => element.style.display = display);
	});
}

const showOrHideDOMElements = function (triggerName, show = true) {
	// const positive = show;
	// // FP in Javascript isn't the most readable 😂
	// DOMElementShowHideTriggers.filter(e => e.trigger === triggerName)
	// 	.forEach(({ show, hide }) => {
	// 		show.map(selector => document.querySelectorAll(selector))
	// 			.map(nodeListToArray)
	// 			.reduce((xs, x) => xs.concat(x), [])
	// 			.forEach(element => element.style.display = positive ? 'block' : 'none');

	// 		hide.map(selector => document.querySelectorAll(selector))
	// 			.map(nodeListToArray)
	// 			.reduce((xs, x) => xs.concat(x), [])
	// 			.forEach(element => element.style.display = positive ? 'none' : 'block');
	// 	});
}