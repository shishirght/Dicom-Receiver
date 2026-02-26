# Use the official OpenJDK 17 image as the base image
FROM amazoncorretto:17-alpine3.22

# Set the working directory inside the container
WORKDIR /app

# Copy the Spring Boot jar file into the container
COPY target/*.jar /app/dicom-receiver.jar

# Expose the port your Spring Boot application runs on
EXPOSE 8080

# Command to run the Spring Boot application
ENTRYPOINT ["java", "-jar", "/app/dicom-receiver.jar"]