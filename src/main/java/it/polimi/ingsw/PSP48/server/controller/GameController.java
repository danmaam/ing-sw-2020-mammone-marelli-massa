package it.polimi.ingsw.PSP48.server.controller;

import it.polimi.ingsw.PSP48.server.EndReason;
import it.polimi.ingsw.PSP48.ViewInterface;
import it.polimi.ingsw.PSP48.WorkerValidCells;
import it.polimi.ingsw.PSP48.server.controller.ControllerState.*;
import it.polimi.ingsw.PSP48.server.model.ActionCoordinates;
import it.polimi.ingsw.PSP48.server.Server;
import it.polimi.ingsw.PSP48.server.model.*;
import it.polimi.ingsw.PSP48.server.model.divinities.Divinity;
import it.polimi.ingsw.PSP48.server.model.exceptions.*;
import it.polimi.ingsw.PSP48.observers.ViewObserver;

import java.util.*;
import java.util.function.Consumer;

/**
 * The MVC game controller.
 */

public class GameController implements ViewObserver {
    private final Model model;
    private GameControllerState nextAction;
    private final int roomID;

    private final HashMap<String, ViewInterface> playersViews = new HashMap<>();

    /**
     * Associates each game's player with his view
     *
     * @param name the player's name
     * @param view the player's virtual view
     */
    public void associateViewWithPlayer(String name, ViewInterface view) {
        playersViews.put(name, view);
    }

    /**
     * Initializes the game controller
     *
     * @param m  the game model
     * @param ID the game room id of the match
     */
    public GameController(Model m, int ID) {
        model = m;
        roomID = ID;
    }

    /**
     * Returns the view of a certain player
     *
     * @param playerName the player name
     * @return the player view
     */
    public ViewInterface getPlayerView(String playerName) {
        return playersViews.get(playerName);
    }

    /**
     * Adds a player in the game; if the game's player number is reached, the controller starts the game.
     *
     * @param birthday the player's birthday
     * @param name     the player's name
     * @author Daniele Mammone
     */
    public void addPlayer(String name, Calendar birthday) {
        model.addPlayer(name, model.getNextColour(), birthday);

        if (model.getPlayersInGame().size() == model.getGamePlayerNumber()) {
            if (model.isGameWithDivinities()) this.startGameWithDivinities();
            else this.startGameWithoutDivinities();
        }
    }

    /**
     * Perform the move requested from the player.
     *
     * @param p the coordinates of the move, including which worker the player wants to move, and the cell where the player wants to move the worker
     * @author Daniele Mammone
     */
    @Override
    public void move(ActionCoordinates p) {
        try {
            nextAction = model.getCurrentPlayer().getDivinity().move(p.getWorkerRow(), p.getWorkerColumn(), p.getMoveRow(), p.getMoveColumn(), model);
        } catch (IncorrectLevelException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to go on a too high level. Retry the move.");
            nextAction();
            return;
        } catch (DivinityPowerException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to go on a cell blocked by other divinity power");
            nextAction();
            return;
        } catch (NotAdjacentCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to move on a cell that is not adjacent");
            nextAction();
            return;
        } catch (DomedCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to go on a cell that is domed");
            nextAction();
            return;
        } catch (OccupiedCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to go on a cell occupied by other worker. Retry.");
            nextAction();
            return;
        } catch (NoTurnEndException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("With this move, you can't end the turn. Retry.");
            nextAction();
            return;
        }
        this.postMove();
    }

    /**
     * Perform the build requested from the player.
     *
     * @param p the coordinates of the build, including the used worker (that's the one that the player moved), and the cell where the player wants to move the worker
     * @author Daniele Mammone
     */
    @Override
    public void build(ActionCoordinates p) {
        try {
            nextAction = model.getCurrentPlayer().getDivinity().build(p.getWorkerRow(), p.getWorkerColumn(), p.getMoveRow(), p.getMoveColumn(), model);
        } catch (NotAdjacentCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a cell not adjacent");
            nextAction();
            return;
        } catch (DivinityPowerException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a cell blocked by a divinity power");
            nextAction();
            return;
        } catch (DomedCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a domed cell");
            nextAction();
            return;
        } catch (OccupiedCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a cell occupied by another worker");
            nextAction();
            return;
        } catch (MaximumLevelReachedException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to go over level 3");
            nextAction();
            return;
        }
        this.postBuild();
    }

    /**
     * Perform the build requested from the player.
     *
     * @param p the coordinates of the doming, including the used worker (that's the one that the player moved), and the cell where the player wants to put the dome
     * @author Daniele Mammone
     */
    @Override
    public void dome(ActionCoordinates p) {
        try {
            nextAction = model.getCurrentPlayer().getDivinity().dome(p.getWorkerRow(), p.getWorkerColumn(), p.getMoveRow(), p.getMoveColumn(), model);
        } catch (NotAdjacentCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a cell not adjacent");
            nextAction();
            return;
        } catch (DivinityPowerException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a cell blocked by a divinity power");
            nextAction();
            return;
        } catch (DomedCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a domed cell");
            nextAction();
            return;
        } catch (MaximumLevelNotReachedException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a cell that's already at the maximum level");
            nextAction();
            return;
        } catch (OccupiedCellException e) {
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Trying to build on a cell occupied by another worker");
            nextAction();
            return;
        }
        this.postBuild();
    }

    /**
     * Put a player's worker on the board
     *
     * @param p the cell where the player wants to put the worker.
     */
    @Override
    public void putWorkerOnTable(Position p) {
        try {
            model.getCurrentPlayer().getDivinity().putWorkerOnBoard(p, model).nextState().accept(this);
        } catch (OccupiedCellException e) {
            System.out.println("Positioning on occupied cell");
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Can't put worker here, occupied cell. Retry operation");
            this.requestInitialPositioning();
        } catch (DivinityPowerException e) {
            System.out.println("Positioning on a wrong cell due to divinity power");
            getPlayerView(model.getCurrentPlayer().getName()).printMessage("Can't put worker here, cell not available due to divinity power. Retry operation");
            this.requestInitialPositioning();
        }
    }


    /**
     * method to associate current player with his chosen divinity
     *
     * @param divinity the name of the divinity chosen by the current player
     * @author Daniele Mammone
     */
    //@ requires (\exists int x; 0 <= x && x<= model.getAvailableDivinities().size(); model.getAvailableDivinities().get(x).getName().equals(divinity))
    @Override
    public void registerPlayerDivinity(String divinity) {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " chose " + divinity + "!.\nWait for your turn.");
        }
        model.setPlayerDivinity(model.getCurrentPlayer().getName(), divinity);
        //if the current player is the chosen, all divinities has benn selected, and the game must start
        if (model.getPlayersInGame().indexOf(model.getCurrentPlayer()) == model.getChallengerIndex())
            nextAction = new RequestFirstPlayerSelection();
        else {
            model.setNextPlayer();
            nextAction = new RequestDivinitySelection();
        }
        this.nextAction();
    }


    /**
     * method invoked when the challenger has chosen his divinities
     *
     * @param divinities the divinities chosen by the challenger
     * @author Daniele Mammone
     */
    @Override
    public void selectAvailableDivinities(ArrayList<String> divinities) {
        model.challengerDivinityChoice(divinities);
        model.setNextPlayer();
        this.requestDivinitySelection();
    }

    /**
     * method that sets the first player and starts the game
     *
     * @param playerName the name of the player
     */
    @Override
    public void selectFirstPlayer(String playerName) {
        model.setNextPlayer(playerName);
        model.setFirstPlayerIndex(model.getPlayersInGame().indexOf(model.getPlayer(playerName)));
        this.requestInitialPositioning();
    }


    /**
     * Request to the current player to perform a build
     *
     * @author Daniele Mammone
     */
    public void requestBuildDome() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is doing a build action.\nWait for your turn!");
        }
        ArrayList<Divinity> otherDivinities = new ArrayList<>();
        for (Player p : model.getPlayersInGame()) {
            if (!p.getName().equals(model.getCurrentPlayer().getName())) otherDivinities.add(p.getDivinity());
        }
        Position lW = model.getCurrentPlayer().getLastWorkerMoved();
        ArrayList<WorkerValidCells> build = new ArrayList<>();
        ArrayList<WorkerValidCells> dome = new ArrayList<>();

        //Generates valid cells for the action
        ArrayList<Position> buildCells = model.getCurrentPlayer().getDivinity().getValidCellForBuilding(lW.getRow(), lW.getColumn(), otherDivinities, model.getGameBoard());
        ArrayList<Position> domeCells = model.getCurrentPlayer().getDivinity().getValidCellsToPutDome(lW.getRow(), lW.getColumn(), model.getGameBoard(), otherDivinities);

        //Associate valid cells with the worker only if the worker is able to complete the action
        if (!buildCells.isEmpty()) build.add(new WorkerValidCells(buildCells, lW.getRow(), lW.getColumn()));
        if (!domeCells.isEmpty()) dome.add(new WorkerValidCells(domeCells, lW.getRow(), lW.getColumn()));
        getPlayerView(model.getCurrentPlayer().getName()).requestDomeOrBuild(build, dome);
    }

    /**
     * Obtains valid cells for worker's moving, and requires the player to move his player
     *
     * @author Daniele Mammone
     */
    public void requestMove() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is doing a move action!\nWait for your turn.");
        }
        ArrayList<WorkerValidCells> validCells = new ArrayList<>();
        ArrayList<Position> workersPosition = model.getPlayerPositionsInMap(model.getCurrentPlayer().getName());
        ArrayList<Divinity> otherDivinities = new ArrayList<>();
        for (Player p : model.getPlayersInGame()) {
            if (!p.getName().equals(model.getCurrentPlayer().getName())) otherDivinities.add(p.getDivinity());
        }
        for (Position p : workersPosition) {
            ArrayList<Position> cells = model.getCurrentPlayer().getDivinity().getValidCellForMove(p.getRow(), p.getColumn(), model.getGameBoard(), otherDivinities);
            if (!cells.isEmpty()) validCells.add(new WorkerValidCells(cells, p.getRow(), p.getColumn()));
        }
        getPlayerView(model.getCurrentPlayer().getName()).requestMove(validCells);
    }

    /**
     * Perform controller's next action.
     */
    public void nextAction() {
        nextAction.nextState().accept(this);
    }

    /**
     * Checks if at the end of a move the current player won the game. If yes, put the controller in endGame state; else, changes the turn.
     *
     * @author Daniele Mammone
     */
    public void postMove() {
        boolean endGame = model.getCurrentPlayer().getDivinity().postMoveWinCondition(model);
        if (endGame)
            Server.destroyGameRoom(roomID, model.getCurrentPlayer().getName(), EndReason.win);
        this.nextAction();
    }


    /**
     * Checks after a build if s player in game has won
     *
     * @author Daniele Mammone
     */
    public void postBuild() {
        String playerThatWon = null;
        for (Player p : model.getPlayersInGame()) {
            if (p.getDivinity().postBuildWinCondition(model)) {
                playerThatWon = p.getName();
                break;
            }
        }

        if (playerThatWon != null) {
            nextAction = new EndGame();
            Server.destroyGameRoom(roomID, playerThatWon, EndReason.win);
        } else this.nextAction();
    }

    /**
     * method that handles the activities related to the end of the turn
     * * @author Rebecca Marelli
     */
    public void turnEnd() {
        nextAction = model.getCurrentPlayer().getDivinity().turnEnd();
        nextAction();
    }

    /**
     * Changes the current player with the next player
     */
    public void turnChange() {
        model.setNextPlayer();
        if (model.getCurrentPlayer().getTempDivinity() != null)
            model.getCurrentPlayer().getTempDivinity().preTurnSecondaryDivinityChecks(model);
        nextAction = model.getCurrentPlayer().getDivinity().turnBegin(model);
        this.nextAction();
    }


    /**
     * Starts the game, so, randomically, chose the "chosen", sets the chosen as last player to chose divinities, and asks the chosen to select a set of divinities
     *
     * @author Daniele Mammone
     */
    public void startGameWithDivinities() {
        //i must choose randomically the challenger, than request him to choose divinities
        int i = new Random().nextInt(model.getNumberOfPlayers());
        model.setChallengerIndex(i);
        model.setNextPlayer(i);
        //set the challenger, i must ask him to select divinities
        this.requestChallengerDivinitiesSelection();
    }

    /**
     * starts the game without divinites, so set the first player the younger player, and then requests it's first move
     *
     * @author Daniele Mammone
     */
    public void startGameWithoutDivinities() {
        for (Player p : model.getPlayersInGame()) {
            getPlayerView(p.getName()).printMessage("Game started. Waiting for your turn \nto put your workers on the board");
        }

        Player firstPlayer = model.getPlayersInGame().get(0);
        for (Player p : model.getPlayersInGame()) {
            if (p.getBirthday().compareTo(firstPlayer.getBirthday()) > 0) firstPlayer = p;
        }
        //found the younger player, I need to set him as the first player
        model.setNextPlayer(model.getPlayersInGame().indexOf(firstPlayer));
        model.setFirstPlayerIndex(model.getPlayersInGame().indexOf(firstPlayer));

        //set the basic moves on players, i start the game requesting the initial positioning
        this.requestInitialPositioning();
    }

    /**
     * Request the current player to choose his divinity
     *
     * @author Daniele Mammone
     */
    public void requestDivinitySelection() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is choosing his divinity!");
        }
        getPlayerView(model.getCurrentPlayer().getName()).requestDivinitySelection(model.getAvailableDivinities());
    }

    /**
     * Notifies the challenger's view to request him to choose the first player
     */
    public void requestFirstPlayerSelection() {
        for (Player p : model.getPlayersInGame()) {
            if (model.getPlayersInGame().indexOf(p) != model.getChallengerIndex())
                getPlayerView(p.getName()).printMessage("Challenger is choosing the first player!");
        }
        ArrayList<String> players = new ArrayList<>();
        model.getPlayersInGame().forEach((Player p) -> players.add(p.getName()));
        getPlayerView(model.getPlayersInGame().get(model.getChallengerIndex()).getName()).requestInitialPlayerSelection(players);
    }

    /**
     * Notifies the current player's view to ask him to put one of his workers on the board
     */
    public void requestInitialPositioning() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is putting \nhis workers on the board!");
        }
        getPlayerView(model.getCurrentPlayer().getName()).requestInitialPositioning(model.getCurrentPlayer().getDivinity().validCellsForInitialPositioning(model.getGameBoard()));
    }

    /**
     * If all players put their workers on the board, starts the game requesting the first player to do something depending on
     * his divinity; else, changes the current player and asks the next to perform initial positioning
     *
     * @author Daniele Mammone
     */
    public void initialPositioningTurnChange() {

        model.setNextPlayer();
        //Must check if all the players completed the initial positioning
        if (model.getPlayersInGame().indexOf(model.getCurrentPlayer()) == model.getFirstPlayerIndex()) {
            //it means all the workers are on the board, so I must request the turn begin to the first player
            nextAction = model.getCurrentPlayer().getDivinity().turnBegin(model);
        } else {
            nextAction = new RequestInitialPositioning();
        }
        nextAction();
    }

    /**
     * Request the challenger to choose divinities
     *
     * @author Daniele Mammone
     */
    public void requestChallengerDivinitiesSelection() {
        //game started: I advice other players
        for (Player p : model.getPlayersInGame()) {
            if (model.getPlayersInGame().indexOf(p) != model.getChallengerIndex())
                getPlayerView(p.getName()).printMessage("Game strated! Challenger is choosing divinities!");
        }
        //in the model there is a list of available divinities, I send this to the challenger, so he can choose from them
        getPlayerView(model.getPlayersInGame().get(model.getChallengerIndex()).getName()).requestChallengerDivinitiesSelection(model.getAvailableDivinities(), model.getNumberOfPlayers());
    }


    /**
     * Checks if the optional building is possible. If yes, asks for the player to perform (or skip) the optional building; else, requests the next thing to do
     *
     * @author Daniele Mammone
     */

    public void requestOptionalBuilding() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is doing an optional build action! \nWait for your turn.");
        }
        Position lastWorker = model.getCurrentPlayer().getLastWorkerMoved();
        ArrayList<Divinity> otherDivinities = new ArrayList<>();
        for (Player p : model.getPlayersInGame()) {
            if (!p.getName().equals(model.getCurrentPlayer().getName())) otherDivinities.add(p.getDivinity());
        }
        //the player must use the same worker he moved
        ArrayList<Position> validForBuilding = model.getCurrentPlayer().getDivinity().getValidCellForBuilding(lastWorker.getRow(), lastWorker.getColumn(), otherDivinities, model.getGameBoard());
        ArrayList<Position> validForDoming = model.getCurrentPlayer().getDivinity().getValidCellsToPutDome(lastWorker.getRow(), lastWorker.getColumn(), model.getGameBoard(), otherDivinities);
        //if the optional move isn't possible, the controller put itself in the next state
        if (validForBuilding.isEmpty() && validForDoming.isEmpty()) {
            try {
                nextAction = model.getCurrentPlayer().getDivinity().build(-1, -1, -1, -1, model);
                nextAction();
            } catch (Exception e) {
                System.out.println("Fatal error");
            }
        } else {
            ArrayList<WorkerValidCells> build = new ArrayList<>();
            ArrayList<WorkerValidCells> dome = new ArrayList<>();

            //adds the worker-valid cells association only if a worker is able to complete the action
            if (!validForBuilding.isEmpty())
                build.add(new WorkerValidCells(validForBuilding, model.getCurrentPlayer().getLastWorkerMoved().getRow(), model.getCurrentPlayer().getLastWorkerMoved().getColumn()));
            if (!validForDoming.isEmpty())
                dome.add(new WorkerValidCells(validForDoming, model.getCurrentPlayer().getLastWorkerMoved().getRow(), model.getCurrentPlayer().getLastWorkerMoved().getColumn()));

            getPlayerView(model.getCurrentPlayer().getName()).requestOptionalBuild(build, dome);
        }
    }

    //SPECIFIC DIVINITY BEHAVIOUR

    /**
     * Request the player to perform an optional move, but first checks if it's possible
     *
     * @author Daniele Mammone
     */
    public void requestOptionalMove() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is doing an optional move action. \nWait for your turn!");
        }
        //first, I must check if the optional move is possible
        //the player must use the already moved worker
        ArrayList<Divinity> otherDivinities = new ArrayList<>();
        for (Player p : model.getPlayersInGame()) {
            if (!p.getName().equals(model.getCurrentPlayer().getName())) otherDivinities.add(p.getDivinity());
        }
        ArrayList<Position> validPositionsForMove = model.getCurrentPlayer().getDivinity().getValidCellForMove(model.getCurrentPlayer().getLastWorkerMoved().getRow(), model.getCurrentPlayer().getLastWorkerMoved().getColumn(), model.getGameBoard(), otherDivinities);
        if (validPositionsForMove.isEmpty()) {
            //skips the optional move and goes in the next FSM state if the optional move isn't possible
            try {
                nextAction = model.getCurrentPlayer().getDivinity().move(-1, -1, -1, -1, model);
                nextAction();
            } catch (Exception e) {
                System.out.println("fatal error");
            }
        } else {
            ArrayList<WorkerValidCells> move = new ArrayList<>();
            move.add(new WorkerValidCells(validPositionsForMove, model.getCurrentPlayer().getLastWorkerMoved().getRow(), model.getCurrentPlayer().getLastWorkerMoved().getColumn()));
            getPlayerView(model.getCurrentPlayer().getName()).requestOptionalMove(move);
        }

    }

    /**
     * Notify the view to request the player to complete prometheus initial optional build. Redefined since the optional
     * build is at the beginning of the turn and the player can use all of his workers to do the build action.
     */
    public void PrometheusInitialOptionalBuild() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is doing an optional build action! \nWait for your turn.");
        }
        ArrayList<WorkerValidCells> build = new ArrayList<>();
        ArrayList<WorkerValidCells> dome = new ArrayList<>();

        ArrayList<Divinity> otherDivinities = new ArrayList<>();
        for (Player p : model.getPlayersInGame()) {
            if (!p.getName().equals(model.getCurrentPlayer().getName())) otherDivinities.add(p.getDivinity());
        }

        ArrayList<Position> workersPosition = model.getPlayerPositionsInMap(model.getCurrentPlayer().getName());

        //differently from normal optional build, here we can use all the workers to build
        for (Position p : workersPosition) {
            WorkerValidCells b = new WorkerValidCells(new ArrayList<>(model.getCurrentPlayer().getDivinity().getValidCellForBuilding(p.getRow(), p.getColumn(), otherDivinities, model.getGameBoard())), p.getRow(), p.getColumn());
            WorkerValidCells d = new WorkerValidCells(new ArrayList<>(model.getCurrentPlayer().getDivinity().getValidCellsToPutDome(p.getRow(), p.getColumn(), model.getGameBoard(), otherDivinities)), p.getRow(), p.getColumn());
            if (!b.getValidPositions().isEmpty()) build.add(b);
            if (!d.getValidPositions().isEmpty()) dome.add(d);
        }
        getPlayerView(model.getCurrentPlayer().getName()).requestOptionalBuild(build, dome);
    }

    /**
     * Notify the view to ask the player to complete a move action. Redefined for Prometheus since is the optional build is completed, player
     * can't use all his workers to move, but only the one who completed the build.
     */
    public void PrometheusMovePostOptionalBuild() {
        for (Player p : model.getPlayersInGame()) {
            if (p != model.getCurrentPlayer())
                getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + " is doing a move action! \nWait for your turn.");
        }
        ArrayList<Divinity> otherDivinities = new ArrayList<>();
        for (Player p : model.getPlayersInGame()) {
            if (!p.getName().equals(model.getCurrentPlayer().getName())) otherDivinities.add(p.getDivinity());
        }

        //generates valid cells only for the worker that completed the optional move
        ArrayList<WorkerValidCells> move = new ArrayList<>();
        move.add(new WorkerValidCells(model.getCurrentPlayer().getDivinity().getValidCellForMove(model.getCurrentPlayer().getLastWorkerMoved().getRow(), model.getCurrentPlayer().getLastWorkerMoved().getColumn(), model.getGameBoard(), otherDivinities), model.getCurrentPlayer().getLastWorkerMoved().getRow(), model.getCurrentPlayer().getLastWorkerMoved().getColumn()));
        getPlayerView(model.getCurrentPlayer().getName()).requestMove(move);
    }

    /**
     * method handling the elimination of a player when he loses the game cause he can't complete the turn
     */
    public void currentPlayerCantEndTurn() {
        //the current player can't end the turn
        if (model.getPlayersInGame().size() == 2) {
            //end the game notifying the lose of the current player
            nextAction = new EndGame();
            Server.destroyGameRoom(roomID, model.getCurrentPlayer().getName(), EndReason.lose);
            return;
        } else {
            //sets the maximum allowed player at two
            model.setNumberOfPlayer(model.getGamePlayerNumber() - 1);
            //notifies the loser that lost
            getPlayerView(model.getCurrentPlayer().getName()).endgame("You lose cause you won't be able to end the turn");
            //notifies each other player that the loser is out of the game
            for (Player p : model.getPlayersInGame()) {
                if (!p.getName().equals(model.getCurrentPlayer().getName()))
                    getPlayerView(p.getName()).printMessage(model.getCurrentPlayer().getName() + "lost cause he can't end his turn");
            }
            //removes the loser's nickname from the server
            Server.removeNickname(model.getCurrentPlayer().getName());
            model.unregisterObserver(getPlayerView(model.getCurrentPlayer().getName()));
            model.removePlayer(model.getCurrentPlayer().getName());
            nextAction = new TurnChange();
        }
        nextAction();
    }

    /**
     * Returns the controller's next state.
     *
     * @return the next controller state
     */
    public GameControllerState nextState() {
        return nextAction;
    }

    public void gameEnd() {
        Server.destroyGameRoom(roomID, model.getCurrentPlayer().getName(), EndReason.lose);
    }
}