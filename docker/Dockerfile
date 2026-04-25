######################################################
# ---- Stage 1: Build and Test the Maven project ----
######################################################

FROM maven:3.9.11-amazoncorretto-25 AS build-test-stage

# Set the working directory inside the container
WORKDIR /app

# copy the rest of the source code
COPY . /app

# Run the Maven build
# -U force la re-verification des artefacts ( contourne le cache negatif
# .lastUpdated qui peut faire echouer la resolution juste apres une
# nouvelle release du registre INRAE ).
RUN mvn --batch-mode -U clean install -Dmaven.test.skip=true

RUN ls -la /app/target/

######################################################
# ---- Stage 2: Create a minimal runtime image ---- ##
######################################################

# FROM openjdk:25-jdk AS runtime-stage # DEPRECATED
FROM amazoncorretto:25 AS runtime-stage

# Set the working directory
WORKDIR /app

# Copy the JAR file from the build stage
COPY --from=build-test-stage /app/target/*.jar ./si-ore-jar

# Expose the port that your application will run on (if needed)
EXPOSE 8080

# Command to run the application JAR file
ENTRYPOINT ["java", "-jar", "si-ore-jar"]
