package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;
import ca.ubc.cs317.dict.util.DictStringParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Map<String, Database> databaseMap = new LinkedHashMap<String, Database>();

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        try{
            //makes starting socket, printwriter, and bufferreader for communication
            socket = new Socket(host, port);
            output = new PrintWriter(socket.getOutputStream(), true);
            input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            //checks status to see if we connected
            Status status = Status.readStatus(input);
            if(status.getStatusCode() != 220){
                throw new DictConnectionException("Could not connect to server host:");
            }

        }catch(Exception e){
            e.printStackTrace();
        }

    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        try {
            //closes everything and quits
            output.println("QUIT");
            socket.close();
            output.close();
            input.close();
        }catch (Exception e){
            //does nothing
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        getDatabaseList(); // Ensure the list of databases has been populated

        try{
            //command to server
            output.println("DEFINE " + database.getName() + " \"" + word + "\"");
            output.println("DEFINE " + database.getName() + " " + word + "");

            //checks status
            Status status = Status.readStatus(input);

            //throws error if not correct status
            if (status.getStatusCode() != 150){
                throw new DictConnectionException();
            }else if(status.getStatusCode() == 552){
                throw new DictConnectionException("No Match");
            }

            //populates the table with definitions
            while ((status = Status.readStatus(input)).getStatusCode() == 151) {
                String[] splitted = DictStringParser.splitAtoms(status.getDetails());
                Definition newDef = new Definition(splitted[0], databaseMap.get(splitted[1]));
                set.add(newDef);
                String readline;
                while (!(readline = input.readLine()).equals(String.valueOf("."))) {
                    newDef.appendDefinition(readline);
                }
            }

            input.readLine();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return set;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();


        try{
            //sends commmand to server
            output.println("MATCH " + database.getName() + " " + strategy.getName() + " " + word);

            //checks status
            Status status = Status.readStatus(input);

            //sends an error if wrong status
            if(status.getStatusCode() != 152){
                throw new DictConnectionException("Something went wrong");
            }else if(status.getStatusCode() == 552){
                throw new DictConnectionException("No Match");
            }

            //gets similar words and stores them
            String string;
            while(!(string = input.readLine()).equals( ".")){
                String[] array = DictStringParser.splitAtoms(string);
                set.add(array[1]);
            }
            input.readLine();

        }catch(Exception e){
            e.printStackTrace();
        }

        return set;
    }

    /** Requests and retrieves a list of all valid databases used in the server. In addition to returning the list, this
     * method also updates the local databaseMap field, which contains a mapping from database name to Database object,
     * to be used by other methods (e.g., getDefinitionMap) to return a Database object based on the name.
     *
     * @return A collection of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Database> getDatabaseList() throws DictConnectionException {

        if (!databaseMap.isEmpty()) return databaseMap.values();

        try{
            //command for server
            output.println("SHOW DB");

            //gets status
            Status status = Status.readStatus(input);

            //sends error if status isn't right
            if(status.getStatusCode() != 110){
                throw new DictConnectionException();
            }

            //gets all known databases
            String string;
            while(!(string = input.readLine()).equals(".")){
                String[] array = DictStringParser.splitAtoms(string);
                databaseMap.put(array[0], new Database(array[0], array[1]));
            }
            input.readLine();

        }catch(Exception e){
            e.printStackTrace();
        }

        return databaseMap.values();
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        try{
            //server command
            output.println("SHOW STRAT");

            //gets status
            Status status = Status.readStatus(input);

            //sends error if status isn't right
            if(status.getStatusCode() != 111){
                throw new DictConnectionException();
            }

            //gets strats
            String string;
            while(!(string = input.readLine()).equals(".")){
                    String[] array = DictStringParser.splitAtoms(string);
                    set.add(new MatchingStrategy(array[0], array[1]));
            }
            input.readLine();

        }catch(Exception e){
            e.printStackTrace();
        }

        return set;
    }

}