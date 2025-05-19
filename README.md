# Scorecard SonarQube Source Processing Lambda

This AWS Lambda function processes SonarQube code quality metrics for repositories and stores them in the scorecard system for unified metrics tracking.

## Purpose

The lambda retrieves SonarQube metrics for specific projects, calculates scores based on the quality gate status, and stores these metrics in InfluxDB for visualization and analysis.

## Architecture

The lambda is triggered by SQS messages with a source attribute of "sonarqube". It processes these messages, fetches metrics from the SonarQube API, and stores them in InfluxDB.

### Components

- **LambdaFunction**: Entry point for AWS Lambda, handles SQS events
- **LambdaHandler**: Coordinates the processing of SonarQube metrics
- **SonarQubeService**: Business logic for fetching and processing metrics
- **SonarQubeClient**: Client for interacting with the SonarQube API
- **SonarQubeInfluxRepository**: Repository for storing metrics in InfluxDB

## Configuration

The lambda requires the following environment variables:

- `SONARQUBE_URL`: URL of the SonarQube instance
- `INFLUXDB_URL`: URL of the InfluxDB instance
- `INFLUXDB_ORG`: Organization in InfluxDB
- `INFLUXDB_BUCKET`: Bucket in InfluxDB to store metrics

And the following secrets in AWS Secrets Manager:

- `sonarqube/api-token`: Token for authenticating with the SonarQube API
- `influxdb/api-token`: Token for authenticating with the InfluxDB API

## Input Message Format

The lambda processes SQS messages with the following format:

```json
{
    "source": "sonarqube",
    "projectId": "the-project-id-in-sonarqube",
    "timestamp": "2024-01-27T10:00:00Z"
}
```

## Scoring Logic

The lambda uses a Pass0rFailScoringStrategy:

- PASSED quality gate results in a score of 100
- FAILED quality gate results in a score of 0

## Deployment

The lambda is deployed using the standard deployment process for scorecard lambdas:

1. Build using Maven
2. Package into a deployment bundle
3. Upload to S3 bucket
4. Deploy infrastructure with Terraform
5. Set up CloudWatch alarms

## AWS Snapstart

This lambda uses AWS Snapstart to reduce cold start times. The lambda is configured to use Java 21 and follows AWS Snapstart best practices.

## Development

### Prerequisites

- Java 21
- Maven
- AWS CLI

### Building

```bash
mvn clean package
```

### Testing

```bash
mvn test
```

## Monitoring

The lambda emits the following CloudWatch metrics:

- `ProcessedEvents`: Number of events processed
- `ProcessingErrors`: Number of errors during processing
- `SonarQubeApiErrors`: Number of errors when calling the SonarQube API
- `InfluxDbWriteErrors`: Number of errors when writing to InfluxDB

## References

- [SonarQube Web API](https://docs.sonarqube.org/latest/extend/web-api/)
- [InfluxDB Java Client](https://github.com/influxdata/influxdb-client-java)
- [AWS Lambda with Java](https://docs.aws.amazon.com/lambda/latest/dg/lambda-java.html)
- [AWS Snapstart](https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html)