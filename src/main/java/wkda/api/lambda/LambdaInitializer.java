package wkda.api.lambda;

import lombok.extern.log4j.Log4j2;

/**
 * Initializer for the Lambda function.
 * This class is used to perform initialization during the Lambda cold start.
 */
@Log4j2
public class LambdaInitializer {
    
    /**
     * Static initializer that runs when the class is loaded.
     * This is used to perform initialization during the Lambda cold start.
     */
    static {
        try {
            log.info("Lambda cold start initialization started");
            
            // Load the SnapstartInitializer to register the Snapstart hook
            Class.forName(SnapstartInitializer.class.getName());
            
            // Perform any other initialization here
            
            log.info("Lambda cold start initialization completed successfully");
        } catch (Exception e) {
            log.error("Error during Lambda cold start initialization", e);
        }
    }
    
    /**
     * Private constructor to prevent instantiation.
     */
    private LambdaInitializer() {
        // Private constructor to prevent instantiation
    }
}