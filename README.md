# AR App Characteristics

## Development Infromation
Uses Kotlin code and is run in phones that have android version 11 or 12. (developed for pixel)

## Functionality
The code runs in the background of the app that it launches. Either one phone or two phones can be required for getting the characteristics.
The following single phone characterisitics are derived,
1. Battery Discharged
2. CPU usage
3. Energy used

The following multiple phones characteristics are observed,
1. Upload Bandwidth
2. Download Bandwidth
3. Latency


**NOTE: The latency of an operation (Time taken for a certain operation on one phone to reflect on the other) is calculated using bandwidth and is done with justaline 2.0 in mind. (Based on requirement this can be changed).**

**The JAVA code is in ARCharacterisits.**
