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
];

const setDOMElementVisibility = function (myGameState, serverGameState) {
	selectorsToShowOrHide.forEach(({ selector, styleFn }) => {
		const display = styleFn(myGameState, serverGameState);
		document.querySelectorAll(selector)
			.forEach(element => element.style.display = display);
	});
}