/* This file contains functions which send requests to the server.
*/

async function sendRequest(requestName, data = null) {
    let fetchArgs = null;
    if (data !== null)
        fetchArgs = { method: 'POST', body: data };

    const fetchResult = await fetch(requestName, fetchArgs)
        .catch(err => {
            // Can show an error page here that says we failed to contact the server
            console.log(err);   
        });

    if (fetchResult
        && fetchResult.headers
        && fetchResult.headers.get('content-type')
        && fetchResult.headers.get('content-type').toLowerCase().indexOf('application/json') >= 0) {

        const response = await fetchResult.json();

        if (response
            && response.Error != null) {
            if (response.Error === 'NO_SESSION') {
                setCookie('messageOnReload', 'There was an error. You didn\'t have an active session');
                location.reload();
                console.log(response.Error);
                return null;
            }
            else if (response.Error === 'ILLEGAL_REQUEST') {
                let errorMessage = response.Message;
                if (errorMessage === null) {
                    errorMessage = 'An unknown error occurred';
                }

                setCookie('messageOnReload', errorMessage);
                location.reload();
                return null;
            }
            else {
                console.log(`Unknown error code: ${response.Error}. Full response: ${response}`);
                showNotification(`The server is reporting an error called '${response.Error}', but I don't know what that means :(`);
                return null;
            }
        }

        return response;
    }
}

async function sendUsername(name) {
    const data = JSON.stringify({ username: name });
    const requestResult = await sendRequest('username', data);

    return requestResult;
}

async function sendIWillHost() {
    const requestResult = await sendRequest('hostNewGame');

    return requestResult;
}

async function sendGameParams(numRounds, roundDuration, numNames) {
    const data = JSON.stringify({ numRounds, roundDuration, numNames });
    const requestResult = await sendRequest('gameParams', data);

    return requestResult;
}

function sendAllocateTeamsRequest() {
    sendRequest('allocateTeams');
}

async function sendGameIDResponseRequest(enteredGameID) {
    const data = JSON.stringify({ gameID: enteredGameID });
    const requestResult = await sendRequest('askGameIDResponse', data);

    return requestResult;
}

function sendNameRequest() {
    sendRequest('sendNameRequest');
}

async function sendNameList(nameArr) {
    const data = JSON.stringify({ nameList: nameArr });
    const requestResult = await sendRequest('nameList', data);

    return requestResult;
}

function sendStartGameRequest() {
    sendRequest('startGame')
}

async function sendStartTurnRequest() {
    const requestResult = sendRequest('startTurn');

    return requestResult;
}

async function sendUpdateCurrentNameIndex(newNameIndex) {
    const data = JSON.stringify({ newNameIndex });
    const requestResult = await sendRequest('setCurrentNameIndex', data);

    return requestResult;
}

function sendStartNextRoundRequest() {
    sendRequest('startNextRound');
}

async function sendPassRequest(passNameIndex) {
    const data = JSON.stringify({ passNameIndex });
    const requestResult = await sendRequest('pass', data);

    return requestResult;
}

function sendEndTurnRequest() {
    sendRequest('endTurn');
}

async function sendPutInTeamRequest(playerID, teamIndex) {
    const data = JSON.stringify({ playerID, teamIndex });
    const requestResult = await sendRequest('putInTeam', data);

    return requestResult;
}

async function sendRemoveFromGameRequest(playerID) {
    const data = JSON.stringify({ playerID });
    const requestResult = await sendRequest('removeFromGame', data);

    return requestResult;
}

async function sendMoveInTeamRequest(playerID, moveDownOrLater) {
    const data = JSON.stringify({ playerID });
    const apiCall = moveDownOrLater ? "moveLater" : "moveEarlier";

    const requestResult = await sendRequest(apiCall, data);

    return requestResult;
}

async function sendMakePlayerNextInTeamRequest(playerID) {
    const data = JSON.stringify({ playerID });
    const requestResult = await sendRequest('makeNextInTeam', data);

    return requestResult;
}

async function sendMakeThisTeamNextRequest(teamIndex) {
    const data = JSON.stringify({ index: teamIndex });
    const requestResult = await sendRequest('setTeamIndex', data);

    return requestResult;
}