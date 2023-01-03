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