This project uses Intellij to write and build. 

Alternatively, we can use maven. 
- To build jar file, run this command from base directory of the project: 
``` 
mvn clean package 
```

- The JAR file will be built into target/final folder. To run the JAR file, first enters 
directory that contains parks.csv and all-campsites.csv because the JAR file expects 
data from these two files. Then, run the JAR file:
```
java -jar target/final/Campspot.jar
```

or these commands will use CSV files inside target final folder:
```
cd target/final
java -jar Campspot.jar
```