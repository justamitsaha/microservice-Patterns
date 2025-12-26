# Encrypting values in Config Server
1. Step 1 encrypt
    ```curl -X POST http://localhost:8888/encrypt -H "Content-Type: text/plain" -d "10"```

2. Step 1 decrypt to verify 
     ```curl -X POST http://localhost:8888/decrypt -H "Content-Type: text/plain" -d "faf6fb5b51f0cb840fa5104b7a85a6bbbc8e9980da059dff5a6b6febda3e60f7"```

3.  Step 2 Set value in properties file

4.  Step 3 Do bus refresh
     ```curl -X POST http://localhost:8888/actuator/busrefresh```