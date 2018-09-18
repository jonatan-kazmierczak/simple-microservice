package demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static demo.Utils.log;


/**
 * Loads recorded responses into {@link #apiResponses}.
 */
public class DataLoader {
    private static final Path inputPath = Paths.get("./api-responses");
    private static final String RESPONSE_START = "START ";
    private static final String RESPONSE_END = "END   ";
    private static final int PREFIX_LENGTH = RESPONSE_START.length();

    private final Config config;

    /** map[apiURI][startTimestampInSeconds] = ApiResponse */
    final Map<URI, List<ApiResponse>> apiResponses = new HashMap<>();


    public DataLoader(Config config) {
        long startTime = System.currentTimeMillis();
        this.config = config;

        try ( Stream<Path> recordFiles = Files.list(inputPath).sorted() ) {
            recordFiles.forEach( this::load );
        } catch (IOException e) {
            log(e);
            throw new UncheckedIOException(e);
        }
        log( "LOADED apiResponses for: %s", apiResponses.keySet() );
        long loadTime = System.currentTimeMillis() - startTime;
        log( "LOADED within %d ms %n", loadTime );
    }

    private void load(Path file) {
        log("PROCESSING %s", file);
        String apiPath = extractApiPath(file);
        log("Extracted %s", apiPath);

        List<ApiResponse> responses = new LinkedList<>();

        try ( BufferedReader recordReader = Files.newBufferedReader(file) ) {
            ApiResponse response = new ApiResponse();
            for ( String line = recordReader.readLine(); line != null; line = recordReader.readLine() ) {
                if ( line.startsWith( RESPONSE_START ) ) {
                    response.startTimestamp = extractTimestamp(line);
                } else if ( line.startsWith( RESPONSE_END ) ) {
                    response.setEndTimestamp( extractTimestamp(line) );
                    responses.add(response);
                    //log(response);
                    response = new ApiResponse();
                } else if ( !line.isEmpty() ) {
                    response.body.add(line);
                }
            }
        } catch (IOException e) {
            log(e);
            throw new UncheckedIOException(e);
        }

        log("%d requests loaded from '%s'", responses.size(), file);
        responses = convertToTimeBased(responses);
        log("%d seconds loaded from '%s'", responses.size(), file);
        log("");
        apiResponses.put( URI.create(apiPath), responses );
    }

    /*
     * input:  START 1508511755.718194905
     * output: 1508511755718
     */
    private static long extractTimestamp(String line) {
        String[] numbers = line.substring(PREFIX_LENGTH).split("\\.");
        return TimeUnit.SECONDS.toMillis( Long.parseLong( numbers[0] ) ) +
                TimeUnit.NANOSECONDS.toMillis( Long.parseLong( numbers[1] ) );
    }

    /*
     * input:  1508511755-_api_users_profiles.txt
     * output: /api/users/profiles
     */
    private static String extractApiPath(Path file) {
        return file.getFileName()
                .toString()
                .split("[\\-\\.]")[1]
                .replace('_', '/');
    }

    /**
     * Returns list with time[seconds]-based indexes;
     * it means, that each index represents number of seconds, which passed from the beginning of the recording.
     */
    private List<ApiResponse> convertToTimeBased(List<ApiResponse> responses) {
        if (responses.size() < 2) { return responses; }

        ArrayList<ApiResponse> timeBasedResponses = new ArrayList<>( responses.size() + 2 );
        ApiResponse prevResponse = responses.get(0);
        long baseTime = responses.get(0).startTimestamp;

        for (ApiResponse response : responses) {
            response.startTimestamp -= baseTime;
            //log(response);
            // as a side effect, the 1st element is skipped

            // fill the time gap after previous response with its copies
            for (
                    long i = timeMsToS( prevResponse.startTimestamp ) + 1;
                    i < timeMsToS( response.startTimestamp );
                    ++i
            ) { timeBasedResponses.add( prevResponse ); }

            // add the new response if it is at least 1s after the previous one
            if ( timeMsToS( prevResponse.startTimestamp ) < timeMsToS( response.startTimestamp ) ) {
                timeBasedResponses.add( response );
            }

            prevResponse = response;
        }

        // use the last response as the pause
        for (int i = 0; i < config.pauseSeconds; ++i) { timeBasedResponses.add( prevResponse ); }

        //log( timeBasedResponses );
        return timeBasedResponses;
    }

    private static long timeMsToS(long millis) {
        return TimeUnit.MILLISECONDS.toSeconds( millis );
    }
}
