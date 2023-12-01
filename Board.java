package jump61;

import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Formatter;
import static jump61.Side.*;
import static jump61.Square.square;
import static jump61.GameException.*;

/** Represents the state of a Jump61 game.  Squares are indexed either by
 *  row and column (between 1 and size()), or by square number, numbering
 *  squares by rows, with squares in row 1 numbered from 0 to size()-1, in
 *  row 2 numbered from size() to 2*size() - 1, etc. (i.e., row-major order).
 *
 *  A Board may be given a notifier---a Consumer<Board> whose
 *  .accept method is called whenever the Board's contents are changed.
 *
 *  @author Melody Ma
 */
class Board {

    /** An uninitialized Board.  Only for use by subtypes. */
    protected Board() {
        _notifier = NOP;
    }

    /** An N x N board in initial configuration. */
    Board(int N) {
        this();
        _size = N;
        _storage = new Square[_size][_size];
        Square initial = square(WHITE, 1);
        for (int i = 0; i < _size; i++) {
            for (int j = 0; j < _size; j++) {
                _storage[i][j] = initial;
            }
        }
        _history = new ArrayList<>();
        _current = 0;
    }

    /** A board whose initial contents are copied from BOARD0, but whose
     *  undo history is clear, and whose notifier does nothing. */
    Board(Board board0) {
        this(board0.size());
        for (int r = 0; r < _size; r++) {
            for (int c = 0; c < _size; c++) {
                _storage[r][c] = board0.get(r + 1, c + 1);
            }
        }
        _history.clear();
        _current = 0;
        _notifier = (s) -> { };
        _readonlyBoard = new ConstantBoard(this);
    }

    /** Returns a readonly version of this board. */
    Board readonlyBoard() {
        return _readonlyBoard;
    }

    /** (Re)initialize me to a cleared board with N squares on a side. Clears
     *  the undo history and sets the number of moves to 0. */
    void clear(int N) {
        this._size = N;
        _storage = new Square[_size][_size];
        Square initial = square(WHITE, 1);
        for (int i = 0; i < _size; i++) {
            for (int j = 0; j < _size; j++) {
                _storage[i][j] = initial;
            }
        }
        _history = new ArrayList<>();
        _current = 0;
        announce();
    }

    /** Copy the contents of BOARD into me. */
    void copy(Board board) {
        _current = 0;
        if (!(_history == null)) {
            _history.clear();
        }
        internalCopy(board);
    }

    /** Copy the contents of BOARD into me, without modifying my undo
     *  history. Assumes BOARD and I have the same size. */
    private void internalCopy(Board board) {
        assert size() == board.size();
        for (int r = 0; r < _size; r++) {
            for (int c = 0; c < _size; c++) {
                _storage[r][c] = board.get(r + 1, c + 1);
            }
        }
    }

    /** Return the number of rows and of columns of THIS. */
    int size() {
        return _size;
    }

    /** Returns the contents of the square at row R, column C
     *  1 <= R, C <= size (). */
    Square get(int r, int c) {
        return get(sqNum(r, c));
    }

    /** Returns the contents of square #N, numbering squares by rows, with
     *  squares in row 1 number 0 - size()-1, in row 2 numbered
     *  size() - 2*size() - 1, etc. */
    Square get(int n) {
        int row = row(n);
        int col = col(n);
        return _storage[row - 1][col - 1];
    }

    /** Returns the total number of spots on the board. */
    int numPieces() {
        int numSpots = 0;
        for (int i = 0; i < _size * _size; i++) {
            numSpots += get(i).getSpots();
        }
        return numSpots;
    }

    /** Returns the Side of the player who would be next to move.  If the
     *  game is won, this will return the loser (assuming legal position). */
    Side whoseMove() {
        return ((numPieces() + size()) & 1) == 0 ? RED : BLUE;
    }

    /** Return true iff row R and column C denotes a valid square. */
    final boolean exists(int r, int c) {
        return 1 <= r && r <= size() && 1 <= c && c <= size();
    }

    /** Return true iff S is a valid square number. */
    final boolean exists(int s) {
        int N = size();
        return 0 <= s && s < N * N;
    }

    /** Return the row number for square #N. */
    final int row(int n) {
        return n / size() + 1;
    }

    /** Return the column number for square #N. */
    final int col(int n) {
        return n % size() + 1;
    }

    /** Return the square number of row R, column C. */
    final int sqNum(int r, int c) {
        return (c - 1) + (r - 1) * size();
    }

    /** Return a string denoting move (ROW, COL)N. */
    String moveString(int row, int col) {
        return String.format("%d %d", row, col);
    }

    /** Return a string denoting move N. */
    String moveString(int n) {
        return String.format("%d %d", row(n), col(n));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
        to square at row R, column C. */
    boolean isLegal(Side player, int r, int c) {
        return isLegal(player, sqNum(r, c));
    }

    /** Returns true iff it would currently be legal for PLAYER to add a spot
     *  to square #N. */
    boolean isLegal(Side player, int n) {
        if (!exists(n)) {
            return false;
        } else if (get(n).getSide() == player.opposite()) {
            return false;
        } else {
            return getWinner() == null;
        }
    }

    /** Returns true iff PLAYER is allowed to move at this point. */
    boolean isLegal(Side player) {
        if (getWinner() == player.opposite()) {
            return false;
        } else {
            Side turn = whoseMove();
            if (turn == player) {
                return true;
            }
            return false;
        }
    }

    /** Returns the winner of the current position, if the game is over,
     *  and otherwise null. */
    final Side getWinner() {
        if (numOfSide(RED) == _size * _size) {
            return RED;
        } else if (numOfSide(BLUE) == _size * _size) {
            return BLUE;
        } else {
            return null;
        }
    }

    /** Return the number of squares of given SIDE. */
    int numOfSide(Side side) {
        int sum = 0;
        for (int i = 0; i < _size * _size; i++) {
            Square curr = get(i);
            if (curr.getSide() == side) {
                sum += 1;
            }
        }
        return sum;
    }

    /** Add a spot from PLAYER at row R, column C.  Assumes
     *  isLegal(PLAYER, R, C). */
    void addSpot(Side player, int r, int c) {
        if (!isLegal(player, r, c)) {
            throw error("illegal move");
        }
        markUndo();
        Square curr = get(r, c);
        if (curr.getSpots() + 1 <= neighbors(r, c)) {
            Square input = square(player, curr.getSpots() + 1);
            _storage[r - 1][c - 1] = input;
        } else {
            Square input = square(player, curr.getSpots() + 1);
            _storage[r - 1][c - 1] = input;
            jump(sqNum(r, c));
        }
        _current += 1;
    }

    /** Add a spot from PLAYER at square #N.  Assumes isLegal(PLAYER, N). */
    void addSpot(Side player, int n) {
        addSpot(player, row(n), col(n));
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white). */
    void set(int r, int c, int num, Side player) {
        internalSet(r, c, num, player);
        announce();
    }

    /** Set the square at row R, column C to NUM spots (0 <= NUM), and give
     *  it color PLAYER if NUM > 0 (otherwise, white).  Does not announce
     *  changes. */
    private void internalSet(int r, int c, int num, Side player) {
        internalSet(sqNum(r, c), num, player);
    }

    /** Set the square #N to NUM spots (0 <= NUM), and give it color PLAYER
     *  if NUM > 0 (otherwise, white). Does not announce changes. */
    private void internalSet(int n, int num, Side player) {
        if (num == 0) {
            Square input = square(WHITE, 0);
            _storage[row(n) - 1][col(n) - 1] = input;
        } else {
            Square input = square(player, num);
            _storage[row(n) - 1][col(n) - 1] = input;
        }
    }

    /** Undo the effects of one move (that is, one addSpot command).  One
     *  can only undo back to the last point at which the undo history
     *  was cleared, or the construction of this Board. */
    void undo() {
        if (_current > 0) {
            _current -= 1;
            if (_current == -1) {
                _history.remove(_current);
                Board lastBoard = new Board(_size);
                internalCopy(lastBoard);
            } else {
                Board lastBoard = _history.remove(_current);
                internalCopy(lastBoard);
            }
        }
    }

    /** Record the beginning of a move in the undo history. */
    private void markUndo() {
        Board newBoard = new Board(_size);
        newBoard.internalCopy(this);
        _history.add(newBoard);
    }

    /** Add DELTASPOTS spots of side PLAYER to row R, column C,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int r, int c, int deltaSpots) {
        internalSet(r, c, deltaSpots + get(r, c).getSpots(), player);
    }

    /** Add DELTASPOTS spots of color PLAYER to square #N,
     *  updating counts of numbers of squares of each color. */
    private void simpleAdd(Side player, int n, int deltaSpots) {
        internalSet(n, deltaSpots + get(n).getSpots(), player);
    }

    /** Used in jump to keep track of squares needing processing.  Allocated
     *  here to cut down on allocations. */
    private final ArrayDeque<Integer> _workQueue = new ArrayDeque<>();

    /** Do all jumping on this board, assuming that initially, S is the only
     *  square that might be over-full. */
    private void jump(int S) {
        while (exists(S)) {
            if (numOfSide(RED) == _size * _size
                    || numOfSide(BLUE) == _size * _size) {
                break;
            } else if (get(S).getSpots() <= neighbors(S)) {
                break;
            } else {
                Square curr = get(S);
                int currSpots = curr.getSpots();
                Side currSide = curr.getSide();
                int numNeighbors = neighbors(S);
                Square input = square(currSide, currSpots - numNeighbors);
                _storage[row(S) - 1][col(S) - 1] = input;
                ArrayList<Integer> allNeighbors = getNeighbor(S);
                for (int i = 0; i < numNeighbors; i++) {
                    int currNeighbor = allNeighbors.get(i);
                    simpleAdd(currSide, currNeighbor, 1);
                    jump(allNeighbors.get(i));
                }
            }
        }
    }

    /** Helper function of get all the neighbors of square S. */
    /** set parameter.
     * @param S square number.
     * @return arraylist of all neighbors.
     */
    private ArrayList<Integer> getNeighbor(int S) {
        int row = row(S);
        int col = col(S);
        ArrayList<Integer> result = new ArrayList<>();
        if (S == 0) {
            result.add(1);
            result.add(_size);
        } else if (S >= 1 && S <= _size - 2) {
            result.add(S - 1);
            result.add(S + 1);
            result.add(S + _size);
        } else if (S == _size - 1) {
            result.add(S - 1);
            result.add(S + _size);
        } else if (S % _size == 0 && S >= _size && S < _size * _size - _size) {
            result.add(S - _size);
            result.add(S + _size);
            result.add(S + 1);
        } else if ((S + 1) % _size == 0 && S > _size
                && (S + 1) < _size * _size) {
            result.add(S - _size);
            result.add(S + _size);
            result.add(S - 1);
        } else if (S == _size * _size - _size) {
            result.add(S - _size);
            result.add(S + 1);
        } else if (S > _size * _size - _size && S < _size * _size - 1) {
            result.add(S + 1);
            result.add(S - 1);
            result.add(S - _size);
        } else if (S == _size * _size - 1) {
            result.add(S - _size);
            result.add(S - 1);
        } else {
            result.add(S - 1);
            result.add(S + 1);
            result.add(S + _size);
            result.add(S - _size);
        }
        return result;
    }

    /** Returns my dumped representation. */
    @Override
    public String toString() {
        Formatter out = new Formatter();
        out.format("%s%s%s", "=", "=", "=");
        out.format("\n");
        for (int row = 1; row <= _size; row++) {
            out.format("%s", "    ");
            for (int col = 1; col <= _size; col++) {
                if (get(row, col).getSide() == RED) {
                    out.format("%d%s", get(row, col).getSpots(), "r");
                } else if (get(row, col).getSide() == BLUE) {
                    out.format("%d%s", get(row, col).getSpots(), "b");
                } else {
                    out.format("%d%s", get(row, col).getSpots(), "-");
                }
                out.format("%s", " ");
            }
            out.format("\n");
        }
        out.format("%s%s%s", "=", "=", "=");
        out.format("\n");
        return out.toString();
    }

    /** Returns an external rendition of me, suitable for human-readable
     *  textual display, with row and column numbers.  This is distinct
     *  from the dumped representation (returned by toString). */
    public String toDisplayString() {
        String[] lines = toString().trim().split("\\R");
        Formatter out = new Formatter();
        for (int i = 1; i + 1 < lines.length; i += 1) {
            out.format("%2d %s%n", i, lines[i].trim());
        }
        out.format("  ");
        for (int i = 1; i <= size(); i += 1) {
            out.format("%3d", i);
        }
        return out.toString();
    }

    /** Returns the number of neighbors of the square at row R, column C. */
    int neighbors(int r, int c) {
        int size = size();
        int n;
        n = 0;
        if (r > 1) {
            n += 1;
        }
        if (c > 1) {
            n += 1;
        }
        if (r < size) {
            n += 1;
        }
        if (c < size) {
            n += 1;
        }
        return n;
    }

    /** Returns the number of neighbors of square #N. */
    int neighbors(int n) {
        return neighbors(row(n), col(n));
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Board)) {
            return false;
        } else {
            Board B = (Board) obj;
            if (_size != B._size) {
                return false;
            } else {
                for (int i = 0; i < _size * _size; i++) {
                    if (!get(i).equals(B.get(i))) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    @Override
    public int hashCode() {
        return numPieces();
    }

    /** Set my notifier to NOTIFY. */
    public void setNotifier(Consumer<Board> notify) {
        _notifier = notify;
        announce();
    }

    /** Take any action that has been set for a change in my state. */
    private void announce() {
        _notifier.accept(this);
    }

    /** A notifier that does nothing. */
    private static final Consumer<Board> NOP = (s) -> { };

    /** A read-only version of this Board. */
    private ConstantBoard _readonlyBoard;

    /** Use _notifier.accept(B) to announce changes to this board. */
    private Consumer<Board> _notifier;

    /** Size of the board. */
    private int _size;

    /** Storage of all the squares. */
    private Square[][] _storage;

    /** History of all the moves. */
    private ArrayList<Board> _history;

    /** The position of the current state in _history. */
    private int _current;

}
