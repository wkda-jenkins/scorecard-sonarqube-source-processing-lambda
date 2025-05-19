package wkda.api.lambda;

import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import lombok.extern.log4j.Log4j2;

/**
 * Initializer for AWS Snapstart.
 * This class is used to perform initialization during the Snapstart restore phase.
 */
@Log4j2
public class SnapstartInitializer {
    
    /**
     * Static initializer that runs when the class is loaded.
     * This is used to register a hook that runs during the Snapstart restore phase.
     */
    static {
        try {
            // Register a hook that runs during the Snapstart restore phase
            LambdaRuntime.getRuntime().registerHook(SnapstartInitializer::onRestore);
        } catch (Exception e) {
            log.warn("Failed to register Snapstart hook", e);
        }
    }
    
    /**
     * Method that runs during the Snapstart restore phase.
     * This is used to perform initialization that should happen after the snapshot is restored.
     */
    private static void onRestore() {
        log.info("Snapstart restore phase started");
        
        try {
            // Reset any state that should not be persisted across invocations
            resetState();
            
            log.info("Snapstart restore phase completed successfully");
        } catch (Exception e) {
            log.error("Error during Snapstart restore phase", e);
        }
    }
    
    /**
     * Resets any state that should not be persisted across invocations.
     */
    private static void resetState() {
        // Reset any static state here
        // For example, reset connection pools, clear caches, etc.
    }
}