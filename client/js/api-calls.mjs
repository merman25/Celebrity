/* This file contains functions which send requests to the server.
 * All functions in here should use async/await, since they will be called by
 * the event listener added by addServerRequestClickListener, and it will use await
 * when calling the api function.
*/

import * as common from './script.mjs';

/* Function to send a request to the server. All other functions in this file delegate
 * to this function. It provides error handling by ensuring the appropriate notification
 * is shown to the user if the server returns an error response.
*/
async function sendRequest(requestName, data = null) {
    let fetchArgs = null;
    if (data !== null)
        fetchArgs = { method: 'POST', body: data };

    const fetchResult = await fetch(requestName, fetchArgs)
        .catch(err => {
            // Can show an error page here that says we failed to contact the server
            console.error(err);   
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

                // setCookie('messageOnReload', errorMessage);
                // location.reload();
                common.showNotification(errorMessage);
                common.restoreWebsocketIfNecessary();
                return null;
            }
            else {
                console.log(`Unknown error code: ${response.Error}. Full response: ${response}`);
                common.showNotification(`The server is reporting an error called '${response.Error}', but I don't know what that means :(`);
                return null;
            }
        }

        return response;
    }
}

export async function sendUsername(name) {
    const data = JSON.stringify({ username: name });
    const requestResult = await sendRequest('username', data);

    return requestResult;
}

export async function sendIWillHost() {
    const requestResult = await sendRequest('hostNewGame');

    return requestResult;
}

export async function sendGameParams(numRounds, roundDuration, numNames) {
    const data = JSON.stringify({ numRounds, roundDuration, numNames });
    const requestResult = await sendRequest('gameParams', data);

    return requestResult;
}

export async function sendAllocateTeamsRequest() {
    await sendRequest('allocateTeams');
}

export async function sendGameIDResponseRequest(enteredGameID) {
    const data = JSON.stringify({ gameID: enteredGameID });
    const requestResult = await sendRequest('askGameIDResponse', data);

    return requestResult;
}

export async function sendNameRequest() {
    await sendRequest('sendNameRequest');
}

export async function sendNameList(nameArr) {
    const data = JSON.stringify({ nameList: nameArr });
    const requestResult = await sendRequest('nameList', data);

    return requestResult;
}

export async function sendStartGameRequest() {
    await sendRequest('startGame')
}

export async function sendStartTurnRequest() {
    const requestResult = sendRequest('startTurn');

    return requestResult;
}

export async function sendUpdateCurrentNameIndex(newNameIndex) {
    const data = JSON.stringify({ newNameIndex });
    const requestResult = await sendRequest('setCurrentNameIndex', data);

    return requestResult;
}

export async function sendStartNextRoundRequest() {
    await sendRequest('startNextRound');
}

export async function sendPassRequest(passNameIndex) {
    const data = JSON.stringify({ passNameIndex });
    const requestResult = await sendRequest('pass', data);

    return requestResult;
}

export async function sendEndTurnRequest() {
    await sendRequest('endTurn');
}

export async function sendPutInTeamRequest(playerID, teamIndex) {
    const data = JSON.stringify({ playerID, teamIndex });
    const requestResult = await sendRequest('putInTeam', data);

    return requestResult;
}

export async function sendRemoveFromGameRequest(playerID) {
    const data = JSON.stringify({ playerID });
    const requestResult = await sendRequest('removeFromGame', data);

    return requestResult;
}

export async function sendMoveInTeamRequest(playerID, moveDownOrLater) {
    const data = JSON.stringify({ playerID });
    const apiCall = moveDownOrLater ? "moveLater" : "moveEarlier";

    const requestResult = await sendRequest(apiCall, data);

    return requestResult;
}

export async function sendMakePlayerNextInTeamRequest(playerID) {
    const data = JSON.stringify({ playerID });
    const requestResult = await sendRequest('makeNextInTeam', data);

    return requestResult;
}

export async function sendMakeThisTeamNextRequest(teamIndex) {
    const data = JSON.stringify({ index: teamIndex });
    const requestResult = await sendRequest('setTeamIndex', data);

    return requestResult;
}

export async function sendGetRestorableGameListRequest() {
    const requestResult = await sendRequest('getRestorableGameList');
    return requestResult;
}

export async function sendRestoreGameRequest(gameID) {
    const data = JSON.stringify({ gameID });
    const requestResult = await sendRequest('restoreGame', data);

    return requestResult;
}

export async function sendMakePlayerHostRequest(playerID) {
    const data = JSON.stringify({ playerID });
    const requestResult = await sendRequest('makePlayerHost', data);

    return requestResult;   
}

export async function sendRevokeSubmittedNamesRequest() {
    return await sendRequest('revokeSubmittedNames');
}

export async function sendSetCelebrityNameDisplayRequest(displayBool) {
    const data = JSON.stringify( {displayNames: displayBool} );
    const requestResult = await sendRequest('setDisplayCelebrityNames', data);

    return requestResult;
}