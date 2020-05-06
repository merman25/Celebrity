function sendUsername(name) {
    fetch('username', { method: 'POST', body: 'username=' + name })
        .catch(err => console.error(err));
}

async function sendIWillHost() {
    const fetchResult = await fetch('hostNewGame');
    const resultObject = await fetchResult.json();

    return resultObject;
}

async function retrieveGameStateByHTTP() {
    const fetchResult = await fetch('requestGameState', { method: 'POST', body: 'gameID=' + gameID });
    const result = await fetchResult.json();

    return result;
}

function sendGameParams(numRounds, roundDuration, numNames) {
    fetch('gameParams', { method: 'POST', body: `numRounds=${numRounds}&roundDuration=${roundDuration}&numNames=${numNames}` })
        .catch(err => console.error(err));
}

function sendAllocateTeamsRequest() {
    fetch('allocateTeams')
        .catch(err => console.error(err));
}

async function sendGameIDResponseRequest(enteredGameID) {
    const fetchResult = await fetch('askGameIDResponse', { method: 'POST', body: 'gameID=' + enteredGameID });
    const result = await fetchResult.json();

    return result;
}

function sendNameRequest() {
    fetch('sendNameRequest')
        .catch(err => console.error(err));
}

function sendNameList(nameArr) {
    let requestBody = '';
    nameArr.forEach((name, i) => {
        if (requestBody.length > 0) requestBody += '&';
        const paramName = `name${i + 1}`;
        requestBody += paramName;
        requestBody += '=';
        requestBody += name;
    });

    fetch('nameList', { method: 'POST', body: requestBody })
        .catch(err => console.error(err));
}

function sendStartGameRequest() {
    fetch('startGame')
        .catch(err => console.error(err));
}

function sendStartTurnRequest() {
    fetch('startTurn')
        .catch(err => console.error(err));
}

function sendUpdateCurrentNameIndex(newNameIndex) {
    fetch('setCurrentNameIndex', { method: 'POST', body: 'newNameIndex=' + newNameIndex })
        .catch(err => console.error(err));
}

function sendStartNextRoundRequest() {
    fetch('startNextRound')
        .catch(err => console.error(err));
}

async function sendPassRequest(passNameIndex) {
    const fetchResult = await fetch('pass', { method: 'POST', body: 'passNameIndex=' + passNameIndex });
    const result = await fetchResult.json();

    return result;
}

function sendEndTurnRequest() {
    fetch('endTurn')
        .catch(err => console.error(err));
}

function sendPutInTeamRequest(playerID, teamIndex) {
    fetch('putInTeam', { method: 'POST', body: 'playerID=' + playerID + '&teamIndex=' + teamIndex })
        .catch(err => console.error(err));
}

function sendRemoveFromGameRequest(playerID) {
    fetch('removeFromGame', { method: 'POST', body: 'playerID=' + playerID })
        .catch(err => console.error(err));
}

function sendMoveInTeamRequest(playerID, moveDownOrLater) {
    const apiCall = moveDownOrLater ? "moveLater" : "moveEarlier";
    fetch(apiCall, { method: 'POST', body: 'playerID=' + playerID })
        .catch(err => console.error(err));
}

function sendMakePlayerNextInTeamRequest(playerID) {
	fetch('makeNextInTeam', { method: 'POST', body: 'playerID=' + playerID })
		.catch(err => console.error(err));
}
