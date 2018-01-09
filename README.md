# t4sr-mirto-code

This repository contains the code of a Java graphic application developed as part of my bachelor's thesis: "Distributed Justified Decision Making in Swarm Robotics: Logic, Simulation and Implementation". This project tackles the problem of reaching a common decision in an unsupervised community of robots relying only on local rules. In particular, the problem statement here considered requires them to decide whether a property φ holds or not for their world. The proposed approach heavily relies on epistemic logic and inter-agent communication, and offers a solution that consists in gathering local observations and spreading the agents’ beliefs through the swarm until a member can assess the reaching of a state of distributed knowledge.

## MIRTO application

All files under mirto can be run into the Raspberry Pi installed on a robot of type [MIRTO (MIddlesex Robotic PlaTfOrm) version 3](http://www.rmnd.net/the-middlesex-robotic-platform-mirto-version-3/) to run multiple robots as a swarm trying to understand the color of the floor by running the algorithm developed in my bachelor thesis. A sample experiment can be found [here](https://youtu.be/dtD2PuteBD0).

## Supervisor application

The supervisor application can show on a laptop connected to the same WiFi network as the MIRTOs the status of their action. A screen capture of the application can be found, together with the application, [here](https://youtu.be/dtD2PuteBD0)

## Requirements of the MIRTO code

In order to interact with the robotics part of the MIRTO platform version 3, a software running on the Raspberry Pi must interface with the underlying PCB and microcontroller. The [Arduino Service Interface Protocol (ASIP)](https://github.com/fraimondi/java-asip) was developed by Middlesex University for this purpose, and can also be used by any software willing to interface with similar hardware. Libraries for this protocol have already been implemented for the Java, Racket and Erlang programming languages, and Java has been used for this project. The main reason behind the choice of using Java over the two other languages was that [libraries for XBee modules](https://github.com/digidotcom/XBeeJavaLibrary) have also been developed for this language. Moreover, to get an overview of the state of the swarm, the Wi-Fi dongle installed on the robots has been used to send messages to a supervisor. The supervisor machine would run a Java application, also developed as a part of this project, to receive the messages and show a representation of the state of the swarm. Note that, however, the presence of a supervisor is not necessary for the swarm to function. 
For the project to run, the software requirements are:

* the Java ASIP library;
* the Java XBee library.

Which, in turn, need other 3 libraries to run:

* the [Java Simple Serial Connector (JSSC)](https://github.com/scream3r/java-simple-serial-connector), needed by the ASIP library;
* the [Java RXTX library](http://fizzed.com/oss/rxtx-for-java), that allows serial and parallel communication using a native (JNI) implementation, needed by the XBee library. For best compatibility results, the native library should be compiled from source. Visit [this link](http://rxtx.qbang.org/wiki/index.php/Installation_on_Linux) for further details.
* the [Simple Logging Facade for Java (SLF4J)](https://www.slf4j.org/), needed by the XBee library.

