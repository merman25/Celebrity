const selectorsToShowOrHide = [
	{
		selector: '#divJoinOrHost',
		styleFn: (myGameState, serverGameState) => myGameState.myName && !serverGameState.gameID,
	},
	{
		selector: '#divChooseName',
		styleFn: (myGameState, serverGameState) => ! myGameState.myName,
	},
	{
		selector: '#joinGameForm',
		styleFn: (myGameState, serverGameState) => myGameState.willJoin && !serverGameState.gameID,
	},
	{
		selector: '#join',
		styleFn: (myGameState, serverGameState) => myGameState.myName && !serverGameState.gameID && !myGameState.willJoin && !myGameState.willHost,
		visibleDisplay: '',
	},
	{
		selector: '#host',
		styleFn: (myGameState, serverGameState) => myGameState.myName && !serverGameState.gameID && !myGameState.willJoin && !myGameState.willHost,
		visibleDisplay: '',
	},
	{
		selector: '#hostGameDiv',
		styleFn: (myGameState, serverGameState) => myGameState.myName && serverGameState.gameID && myGameState.iAmHosting,
	},
	{
		selector: '#startTurnButton',
		styleFn: (myGameState, serverGameState) => serverGameState.status === 'READY_TO_START_NEXT_TURN' && myGameState.iAmPlaying && ! myGameState.sendingStartTurn,
	},
	{
		selector: '#startGameButton',
		styleFn: (myGameState, serverGameState) => myGameState.iAmHosting && serverGameState.status === 'WAITING_FOR_NAMES' && serverGameState.numPlayersToWaitFor === 0,
	},
	{
		selector: '#gameParamsForm',
		styleFn: (myGameState, serverGameState) => myGameState.myName && serverGameState.gameID && myGameState.iAmHosting && !myGameState.gameParamsSubmitted && !serverGameState.rounds,
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
			&& serverGameState.status === 'WAITING_FOR_PLAYERS',
	},
	{
		selector: '#allocateTeamsDiv',
		styleFn: (myGameState, serverGameState) =>
			myGameState.myName
			&& serverGameState.gameID
			&& myGameState.iAmHosting
			&& !myGameState.namesRequested
			&& serverGameState.status === 'WAITING_FOR_PLAYERS',
	},
	{
		selector: '#turnControlsDiv',
		styleFn: (myGameState, serverGameState) => serverGameState.status === 'PLAYING_A_TURN' && myGameState.iAmPlaying,
		visibleDisplay: 'flex',
	},
	{
		selector: '#turnControlsDiv',
		styleFn: (myGameState, serverGameState) => document.body.clientWidth < 350,
		mutators: {
			true: element => element.style['flex-direction'] = 'column',
			false: element => element.style['flex-direction'] = 'row',
		},
	},
	{
		selector: '#startNextRoundButton',
		styleFn: (myGameState, serverGameState) => myGameState.iAmHosting && serverGameState.status === 'READY_TO_START_NEXT_ROUND',
	},
	{
		selector: '#nameList',
		styleFn: (myGameState, serverGameState) => serverGameState.status === 'WAITING_FOR_NAMES' && !myGameState.namesSubmitted,
	},
	{
		selector: '#exitGameButton',
		styleFn: (myGameState, serverGameState) => serverGameState.gameID,
	},
];

const setDOMElementVisibility = function (myGameState, serverGameState) {
	selectorsToShowOrHide.forEach(({ selector, styleFn, visibleDisplay, mutators }) => {
		const styleFnResult = styleFn(myGameState, serverGameState);

		let mutator;
		if (mutators == null) {
			const display = !styleFnResult         ? 'none'         :
				            visibleDisplay != null ? visibleDisplay :
					                                         'block';

			mutator = element => element.style.display = display;
		}
		else {
			mutator = mutators[styleFnResult];
		}

		document.querySelectorAll(selector)
			.forEach(mutator);
	});
}