######################################################
# ---- Stage 1: Build and Test the Maven project ----
######################################################

FROM maven:3.9.4-amazoncorretto-21 AS build-test-stage

# Set the working directory inside the container
WORKDIR /app

# copy the rest of the source code
COPY . /app

# Run the Maven build
RUN mvn --batch-mode clean install -Dmaven.test.skip=true

RUN ls -la /app/target/

######################################################
# ---- Stage 2: Create a minimal runtime image ---- ##
######################################################

# FROM amazoncorretto:21 AS runtime-stage
FROM openjdk:21-jdk AS runtime-stage

# Set the working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build-test-stage /app/target/*.jar ./si-ore-jar

# Expose the port that your application will run on (if needed)
EXPOSE 8080

# Command to run the application JAR file
ENTRYPOINT ["java", "-jar", "si-ore-jar"]
