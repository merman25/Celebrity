import * as util from "../util.js"

const g = 'got-it';
const p = 'pass';
const e = 'end-turn';

const playerNames = ['Marvin the Paranoid Android', 'Bender', 'Johnny 5', 'R2D2', 'HAL 9000', 'Wall-E', 'T-1000', 'Soundwave', 'Robocop', 'CHAPPiE'];
// 318 names, taken largely from real games
const celebNames = ['(Oom) Thijs', 'Ada Lovelace', 'Adolf Hitler', 'Agatha Christie', 'Alan Turing', 'Alan Winde', 'Alastair Darling', 'Alexander the Great', 'Ali B.', 'Ali g', 'Alladin', 'Andy Warhol', 'Angela Merkel', 'Anne Frank', 'Annie mg schmidt', 'Arnie', 'Arnold Schwarzenegger', 'Arthur Idris Lewis Vanderham', 'Athol fugard', 'Audrey Hepburn', 'Ayrton Senna', 'Bambi', 'Barbara Windsor', 'Batman', 'Beadle', 'Beethoven', 'Benedict Cumberbatch', 'Beyoncé', 'Big Ears', 'Bilbo Baggins', 'Bill Bryson', 'Billie Eilish', 'Blue', 'Bob Dylan', 'Bojo', 'Boris', 'Boris Johnson', 'Boy George', 'Brad Pitt', 'Bridget Jones', 'Buddha', 'Burgemeester Stulemeijer', 'Buzz Aldrin', 'Captain Blackbeard', 'Captain Hook', 'Captain Jack Sparrow', 'Caravaggio', 'Carey Mulligan', 'Carl Friedrich Gauss', 'Carl Rogers', 'carol vorderman', 'Cate Blanchett', 'Catwoman', 'Charles Darwin', 'Charles glass', 'Cher', 'Chris Hoy', 'Chris Packham', 'Chris Whitty', 'Cinderella', 'Clint Eastwood', 'Colin Firth', 'Dali Lama', 'Darth Vader', 'Dave', 'Dave benson phillips', 'David Attenborough', 'David Beckham', 'David Copperfield', 'Delia Smith', 'Deon Meyer', 'Derren Brown', 'Dido Harding', 'Dieuwertje blok', 'Dieuwertje Blok', 'Diewertje blok', 'Dirk scheeringa', 'Dolly Parton', 'Dominic Cummings', 'Donald duck', 'Donald Duck', 'Donald trump', 'Donald Trump', 'dr zeus', 'Dua lipa', 'Eddie Redmayne', 'Eeyore', 'Elisabeth Warren', 'Elon Musk', 'Elton John', 'Elvis', 'Emily Blunt', 'Emma Thompson', 'Enid Blyton', 'Ernie (van Bert)', 'Erwin Schrodinger', 'ET', 'Ewan McGregor', 'Father Christmas', 'fearne cotton', 'Federer', 'Femke Halsema', 'Florence nightingale', 'Frank sinatra', 'Fred west', 'Freddy Mercury', 'Freek de jonge', 'Freek vonk', 'Galileo', 'Gandalf The Grey', 'Gandhi', 'Gareth Malone', 'Gary glitter', 'Gary Player', 'George Monbiot', 'Getafix', 'Gollum', 'Good King Wenceslas', 'Gordon Ramsey', 'Gorilla', 'Gottfried Leibniz', 'Greta thunberg', 'Hamlet', 'Hansie Cronje', 'Harry Houdini', 'Harry Potter', 'Harry styles', 'Helen of Troy', 'Henny huisman', 'Hillary Swank', 'Hippolyta', 'Hitler', 'Houdini', 'Hugh Jackman', 'Hypatia', 'Iain bolt', 'Ian Hislop', 'Inspector Gadget', 'irvine welsh', 'Isaac Newton', 'Ivanka trump', 'J R R Tolkien', 'Jacinda adern', 'Jacinda Ardern', 'Jack Nicholson', 'Jack the Ripper', 'Jair balsonaro', 'James Bond', 'James Joyce', 'Jamie Oliver', 'Jan', 'Jane Austen', 'Jane Eyre', 'jay z', 'Jemima Puddleduck', 'Jennifer Anisten', 'Jeremy Paxman', 'Jerry Seinfeld', 'Jezus', 'Jildo', 'Jippe', 'JK Rowling', 'Johan Cruijff', 'John F. Kennedy', 'John Le Carre', 'John Maynard Keynes', 'John Oliver', 'John von Neumann', 'Johnny Walker', 'Jonty Rhodes', 'Joseph Fourier', 'Judas', 'Judi Dench', 'Julia Donaldson', 'Justin Timberlake', 'Kiki Bertens', 'Klaas', 'Kristen Stewart', 'Kurt Gödel', 'Kwik, Kwek & Kwak', 'Lassie', 'leona lewis', 'Leonardo de Vinci', 'Leonhard Euler', 'lewis carol', 'Lewis Carroll', 'lisa simpson', 'Lloyd christmas', 'Loch Ness Monster', 'Lord Nelson', 'Luke Skywalker', 'Madame Bovary', 'Maddog Mattis', 'Madonna', 'Malala', 'Malcolm X', 'Margaret Thatcher', 'Marilyn Manson', 'Marilyn Monroe', 'Mark Zuckerberg', 'Martin Luther', 'Martin Luther King', 'Mary', 'Mary of Nazareth', 'Mary Poppins', 'Maya de bij', 'McGuyver', 'Meis', 'Melissa (Tribe)', 'Meryl Streep', 'Michael Jackson', 'Michael Jordan', 'Michael knight', 'Michaelangelo', 'Michel Barnier', 'michelle obama', 'Michelle Obama', 'Mickey mouse', 'Miles Davis', 'Mother Teresa', 'Mother Theresa', 'Mr Bean', 'Napoleon', 'Ned Flanders', 'Neil Armstrong', 'Noah (vriendje v Mischa)', 'Noddy', 'Noel edmonds', 'Norah Jones', 'Odie (uit Garfield)', 'Oom Dagobert', 'Oprah', 'Oprah Winfrey', 'Oscar Wilde', 'Otto', 'Paashaas', 'Papa Smurf', 'Paul McCartney', 'Paul Mescal (Connell in Normal People)', 'Paulien Cornelisse', 'Peppa Pig', 'Peter pan', 'Peter R de Vries', 'Philip Pullman', 'Phoebe Buffet', 'Picasso', 'Piet', 'Piglet', 'Pink Floyd', 'Plato', 'Pocahontas', 'Prince Harry', 'R2D2', 'Rasputin', 'Remy', 'Rhea Seehorn', 'Ricky Gervais', 'Roald Dahl', 'Robin', 'Robin Williams', 'Robyn Williams', 'Roger Daltry', 'Roger federa', 'Rosa Parks', 'Rowan atkinson', 'Rudolph the red nose reindeer', 'russel brand', 'Russell Crowe', 'Ruth Bader Ginsberg', 'Ryan Renolds', 'Sam Neill', 'Samuel L Jackson', 'Sarah Jessica Parker', 'Scrooge', 'Sebastian (lobster in Little Mermaid)', 'Shakespeare', 'Shakira', 'Sharon Dijksma (burgemeester Utrecht))', 'Sherlock Holmes', 'Shrek', 'Sigmund Freud', 'Simba', 'Sinterklaas', 'Siya Kolisi', 'Smaug', 'Sneeuwwitje', 'Snow White', 'Spiderman', 'Stephen Fry', 'Stephen Hawkins', 'Steve Jobs', 'Stevie Wonder', 'Taylor Swift', 'Tess Daley', 'The Gruffalo', 'The Queen (Elizabeth 2)', 'The Very Hungry Caterpillar', 'Thea Beckman', 'Thijs', 'Thijs van leer', 'Thomas the Tank Engine', 'Tigger', 'Tobias Funke', 'Tom Cruise', 'Tony Soprano', 'Trump', 'Ulysses', 'Ursula von der Leyen', 'Venus', 'Vladamir Putin', 'Whitey Houston', 'William Shakespeare', 'Winnie the Pooh', 'Winston Churchill', 'Xerxes', 'Yoda', 'Zoe Ball', 'Zwarte Piet (Black Pete)'];

export const generateGame = function (numPlayers, options = {
    seed: null,
    fastMode: true,
    numRounds: null,
    numNamesPerPlayer: null,
    slowMode: false,
    fullChecksWhenNotInFastMode: true
}) {
    const random = util.generateRandomFunction(options.seed);
    const selectedPlayers = choose(random, numPlayers, playerNames);
    let numNamesPerPlayer = options.numNamesPerPlayer;
    if (!numNamesPerPlayer) {
        numNamesPerPlayer = 1 + Math.floor(10 * random());
    }
    const selectedCelebNames = chooseWithoutReplacement(random, numNamesPerPlayer, numPlayers, celebNames);

    const totalNames = numNamesPerPlayer * numPlayers;
    let numRounds = options.numRounds;
    if (!numRounds) {
        numRounds = 1 + Math.floor(10 * random());
    }
    const turns = [];
    let currentTurn = null;

    for (let roundIndex = 0; roundIndex < numRounds; roundIndex++) {
        currentTurn = null;
        for (let nameIndex = 0; nameIndex < totalNames; ) {
            if (currentTurn === null) {
                currentTurn = [];
                turns.push(currentTurn);
            }

            if (options.slowMode) {
                const roundDurationInSec = 60;
                let roundMarginInSec = 3;
                if (options.fastMode && ! options.fullChecksWhenNotInFastMode) {
                    roundMarginInSec = 10;
                }
                const playDurationInSec = roundDurationInSec - roundMarginInSec;

                let playDurationSoFar=0;
                while(playDurationSoFar < playDurationInSec
                        && nameIndex < totalNames) {
                    let playDurationForThisName = 5 + Math.floor(20 * random());
                    playDurationSoFar += playDurationForThisName;
                    currentTurn.push(playDurationForThisName);

                    if (playDurationSoFar < playDurationInSec) {
                        let randomResult = random();
                        if (randomResult < 0.1) {
                            currentTurn.push(p);
                        }
                        else {
                            currentTurn.push(g);
                            nameIndex++;
                            if (nameIndex >= totalNames) {
                                currentTurn = null;
                            }
                        }
                    }
                    else {
                        currentTurn = null;
                    }
                }

                if (currentTurn) {
                    currentTurn.push(playDurationSoFar - playDurationInSec + roundMarginInSec);
                    currentTurn = null;
                }
            }
            else {
                let randomResult = random();
                if (randomResult < 0.1) {
                    currentTurn.push(p);
                }
                else if (randomResult < 0.75) {
                    currentTurn.push(g);
                    nameIndex++;
                }
                else {
                    currentTurn.push(e);
                    currentTurn = null;
                }
            }
        }
    }

    const gameSpec = {
        description: `${numPlayers}-player random game with ${numRounds} rounds and ${numNamesPerPlayer} names per player`,
        restoredGame: false,
        playerNames: selectedPlayers,
        celebrityNames: selectedCelebNames,
        turnIndexOffset: 0,
        fullChecksWhenNotInFastMode: options.fullChecksWhenNotInFastMode,
        turns: turns,
        numNamesPerPlayer: numNamesPerPlayer,
        numRounds: numRounds,
        slowMode: options.slowMode,
    };

    return gameSpec;
}

function choose(random, n, arr) {
    const arrCopy = [];
    arr.forEach(e => arrCopy.push(e));
    shuffle(random, arrCopy);
    for (let i=0; i<arr.length - n; i++) {
        arrCopy.pop();
    }

    return arrCopy;
}

function chooseWithoutReplacement(random, numElementsPerChoice, numChoices, arr) {
    const arrCopy = [];
    arr.forEach(e => arrCopy.push(e));
    shuffle(random, arrCopy);

    const choiceArr = [];
    for (let choiceIndex=0; choiceIndex<numChoices; choiceIndex++) {
        const thisChoice = [];
        choiceArr.push(thisChoice);

        for (let i=0; i<numElementsPerChoice; i++) {
            thisChoice.push(arrCopy.pop());
        }
    }

    return choiceArr;
}

function shuffle(random, arr) {
    let j, x, i;
    for (i = arr.length - 1; i > 0; i--) {
        j = Math.floor(random() * (i + 1));
        x = arr[i];
        arr[i] = arr[j];
        arr[j] = x;
    }
    return arr;
}