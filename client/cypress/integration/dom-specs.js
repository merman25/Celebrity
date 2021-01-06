/*
This file specifies what should and should not be in the DOM at different times. It should especially be used for elements that need to be checked multiple times throughout the game. One-off checks can simply be in-lined in the code, rather than adding a spec to this file.

The elements of the array DOMSpecs are objects with the properties described below. All predicates mentioned below are Javascript functions of type
[testBotInfo, clientState] => boolean

where testBotInfo is an object inserted by the client application script into the testBotInfoDiv (see script.js/setTestBotInfo()) and read by the testing code (see celebrity-tests.js/retrieveTestBotInfo()), and clientState is an object maintained by the testing code keeping track of where we are in the game (clientState is also the argument to celebrity-tests.js/waitForWakeUpTrigger()).

Properties of elements of DOMSpecs are below. Those marked with a question mark are optional.

- selector: a Cypress/JQuery selector that's a valid argument to cy.get()
- ? visibleWhen: An array of objects with the following properties:
    - predicate: A predicate defining one circumstance in which the element(s) defined by the selector should be visible.
    - ? invertible: A boolean. If true, it should also be checked that the element(s) is/are *not* visible when the predicate is false.
- ? invisibleWhen: An array of objects with the following properties:
    - predicate: A predicate defining one circumstance in which the element(s) defined by the selector should not be visible.
- ? notExistWhen:  An array of objects with the following properties:
    - predicate: A predicate defining one circumstance in which the element(s) defined by the selector should not exist.
- ? containsWhen:  An array of objects with the following properties:
    - predicate: A predicate defining one circumstance in which the element(s) defined by the selector should contain the text specified by the property 'text' or by the property 'textFuction'.
    - ? text: The text that the element(s) should contain
    - ? textFunction: A function [testBotInfo, clientState] => string, returning the text that the element(s) should contain. Note that at least one
    of text or textFunction should be set.
    - ? invertible: A boolean. If true, it should also be checked that the element(s) doesn't/don't contain the text when the predicate is false.
*/

export const DOMSpecs = [
    {
        selector: '[id="gameParamsDiv"]',
        containsWhen: [{
            predicate: (testBotInfo, clientState) => clientState.iAmHosting,
            text: 'You\'re the host.',
            invertible: true
        },

        {
            predicate: (testBotInfo, clientState) => ! clientState.iAmHosting,
            textFunction: (testBotInfo, clientState) => `${clientState.hostName} is hosting.`,
            invertible: true
        },

        {
            predicate: (testBotInfo, clientState) => testBotInfo.gameParamsSet,
            text: 'Settings',
            invertible: true
        },
    
        {
            predicate: (testBotInfo, clientState) => testBotInfo.gameParamsSet,
            text: 'Rounds: 3',
        },

        {
            predicate: (testBotInfo, clientState) => testBotInfo.gameParamsSet,
            text: 'Round duration (sec): 60',
        },
    ]
    },

    {
        selector: '[id="gameIDDiv"]',
        containsWhen: [{
            predicate: (testBotInfo, clientState) => clientState.gameID != null,
            textFunction: (testBotInfo, clientState) => `Game ID: ${clientState.gameID}`
        }]
    },

    {
        selector: '[id="gameInfoDiv"]',
        containsWhen: [{
            predicate: (testBotInfo, clientState) => testBotInfo.teamsAllocated === false,
            text: 'Waiting for others to join...'
        }],
    },

    {
        selector: '[id="gameParamsForm"]',
        invisibleWhen: [{
            predicate: (testBotInfo, clientState) => ! clientState.iAmHosting || testBotInfo.gameParamsSet,
        }],
    },

    {
        selector: '[id="teamsButton"]',
        visibleWhen: [{
            predicate: (testBotInfo, clientState) => clientState.iAmHosting && testBotInfo.gameStatus != null && testBotInfo.gameStatus === 'WAITING_FOR_PLAYERS',
            invertible: true
        }],
    },

    {
        selector: '[id="requestNamesButton"]',
        invisibleWhen: [{
            predicate: (testBotInfo, clientState) => ! clientState.iAmHosting || testBotInfo.gameStatus !== 'WAITING_FOR_PLAYERS'
        }],
    },

    {
        selector: '[id="startGameButton"]',
        invisibleWhen: [{
            predicate: (testBotInfo, clientState) => ! clientState.iAmHosting
        }],
    },
];