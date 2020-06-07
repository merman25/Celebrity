function sendUsername(name) {
    const data = JSON.stringify({ username: name });
    fetch('username', { method: 'POST', body: data })
        .catch(err => console.error(err));
}

async function sendIWillHost() {
    const fetchResult = await fetch('hostNewGame');
    const resultObject = await fetchResult.json();

    return resultObject;
}

function sendGameParams(numRounds, roundDuration, numNames) {
    const data = JSON.stringify({ numRounds, roundDuration, numNames });
    fetch('gameParams', { method: 'POST', body: data })
        .catch(err => console.error(err));
}

function sendAllocateTeamsRequest() {
    fetch('allocateTeams')
        .catch(err => console.error(err));
}

async function sendGameIDResponseRequest(enteredGameID) {
    const data = JSON.stringify({ gameID: enteredGameID });
    const fetchResult = await fetch('askGameIDResponse', { method: 'POST', body: data });
    const result = await fetchResult.json();

    return result;
}

function sendNameRequest() {
    fetch('sendNameRequest')
        .catch(err => console.error(err));
}

function sendNameList(nameArr) {
    const data = JSON.stringify({ nameList: nameArr });

    fetch('nameList', { method: 'POST', body: data })
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
    const data = JSON.stringify({ newNameIndex });
    fetch('setCurrentNameIndex', { method: 'POST', body: data })
        .catch(err => console.error(err));
}

function sendStartNextRoundRequest() {
    fetch('startNextRound')
        .catch(err => console.error(err));
}

async function sendPassRequest(passNameIndex) {
    const data = JSON.stringify({ passNameIndex });
    const fetchResult = await fetch('pass', { method: 'POST', body: data });
    const result = await fetchResult.json();

    return result;
}

function sendEndTurnRequest() {
    fetch('endTurn')
        .catch(err => console.error(err));
}

function sendPutInTeamRequest(playerID, teamIndex) {
    const data = JSON.stringify({ playerID, teamIndex });
    fetch('putInTeam', { method: 'POST', body: data })
        .catch(err => console.error(err));
}

async function sendRemoveFromGameRequest(playerID) {
    const data = JSON.stringify({ playerID });
    await fetch('removeFromGame', { method: 'POST', body: data })
              .catch(err => console.error(err));
}

function sendMoveInTeamRequest(playerID, moveDownOrLater) {
    const data = JSON.stringify({ playerID });
    const apiCall = moveDownOrLater ? "moveLater" : "moveEarlier";
    fetch(apiCall, { method: 'POST', body: data })
        .catch(err => console.error(err));
}

function sendMakePlayerNextInTeamRequest(playerID) {
    const data = JSON.stringify({ playerID });
    fetch('makeNextInTeam', { method: 'POST', body: data })
        .catch(err => console.error(err));
}

function sendMakeThisTeamNextRequest(teamIndex) {
    const data = JSON.stringify({index: teamIndex});
    fetch('setTeamIndex', { method: 'POST', body: data })
        .catch(err => console.error(err));
}