# Java Radar Simulation

## Overview
This project simulates basic radar waveform generation and visualization in **Java** using:
- **Rectangular pulse**
- **Linear Frequency Modulated (LFM) Chirp**
- **Echo simulation**
- **Pulse compression**

The program uses **XChart** for plotting and **Apache Commons Math** for numerical operations.  
A single window displays different simulation graphs, and you can cycle between them using the arrow keys.

---

## Features
- Live waveform generation
- Multiple radar signal types
- Interactive view switching with arrow keys
- Simple and extendable code structure

---

## Requirements
- **Java 17+** (or compatible version)
- **Maven** for dependency management
- VS Code or IntelliJ recommended for development

---

## Installation
1. **Clone the repository**
```bash
git clone https://github.com/YOUR-USERNAME/radar-simulation.git
cd radar-simulation```

2. **Build with Maven**
```bash
mvn clean install
```

3. **Run the program**
```bash
mvn exec:java -Dexec.mainClass="com.example.radar.RadarSimulator"
```

