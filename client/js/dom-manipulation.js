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