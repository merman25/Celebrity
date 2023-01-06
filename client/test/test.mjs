import * as util from '../js/util.mjs';

import * as assert from 'assert';

describe('Team Division', function () {
  it('Should tell whether P players can be divided into T teams, possibly with 1 team differing in size by 1', function() {
    let result;
    result = util.teamDivision(3, 2);
    assert.equal(result.success, false);

    // 12 players, 2 teams of 6
    result = util.teamDivision(12, 2);
    assert.equal(result.success, true);
    assert.equal(result.numEqualTeams, 2);
    assert.equal(result.sizeOfEqualTeams, 6);
    assert.equal(result.sizeOfUnequalTeam, 0);

    // 12 players, 3 teams of 4
    result = util.teamDivision(12, 3);
    assert.equal(result.success, true);
    assert.equal(result.numEqualTeams, 3);
    assert.equal(result.sizeOfEqualTeams, 4);
    assert.equal(result.sizeOfUnequalTeam, 0);

    // 12 players, 4 teams of 3
    result = util.teamDivision(12, 4);
    assert.equal(result.success, true);
    assert.equal(result.numEqualTeams, 4);
    assert.equal(result.sizeOfEqualTeams, 3);
    assert.equal(result.sizeOfUnequalTeam, 0);

    // 12 players into 5 teams doesn't work
    result = util.teamDivision(12, 5);
    assert.equal(result.success, false);

    // 13 players, 3*3 + 4
    result = util.teamDivision(13, 4);
    assert.equal(result.success, true);
    assert.equal(result.numEqualTeams, 3);
    assert.equal(result.sizeOfEqualTeams, 3);
    assert.equal(result.sizeOfUnequalTeam, 4);

    // 11 players, 3*3 + 2
    result = util.teamDivision(11, 4);
    assert.equal(result.success, true);
    assert.equal(result.numEqualTeams, 3);
    assert.equal(result.sizeOfEqualTeams, 3);
    assert.equal(result.sizeOfUnequalTeam, 2);
  });
});

describe('Possible numbers of teams', function () {
  it('Should give expected results', function() {
    assert.equal(util.possibleNumbersOfTeams(1).length, 0);
    assert.equal(util.possibleNumbersOfTeams(2).length, 0);
    assert.equal(util.possibleNumbersOfTeams(3).length, 0);

    assert.deepEqual(util.possibleNumbersOfTeams(4), [2]);
    assert.deepEqual(util.possibleNumbersOfTeams(5), [2]);
    assert.deepEqual(util.possibleNumbersOfTeams(6), [2,3]);
    assert.deepEqual(util.possibleNumbersOfTeams(7), [2,3]);
    assert.deepEqual(util.possibleNumbersOfTeams(8), [2,3,4]);
    assert.deepEqual(util.possibleNumbersOfTeams(9), [2,3,4]);
    assert.deepEqual(util.possibleNumbersOfTeams(10), [2,3,5]);
    assert.deepEqual(util.possibleNumbersOfTeams(11), [2,3,4,5]);
    assert.deepEqual(util.possibleNumbersOfTeams(12), [2,3,4,6]);
    assert.deepEqual(util.possibleNumbersOfTeams(13), [2,3,4,6]);
    assert.deepEqual(util.possibleNumbersOfTeams(14), [2,3,5,7]);
    assert.deepEqual(util.possibleNumbersOfTeams(15), [2,3,4,5,7]);
    assert.deepEqual(util.possibleNumbersOfTeams(16), [2,3,4,5,8]);
    assert.deepEqual(util.possibleNumbersOfTeams(17), [2,3,4,6,8]);
    assert.deepEqual(util.possibleNumbersOfTeams(18), [2,3,6,9]);
    assert.deepEqual(util.possibleNumbersOfTeams(19), [2,3,4,5,6,9]);
    assert.deepEqual(util.possibleNumbersOfTeams(20), [2,3,4,5,7,10]);
  });
});