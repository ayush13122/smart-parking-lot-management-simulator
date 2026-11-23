# Project Overview

## Project Title
Smart Parking Lot Management Simulator

## Course
CSM228 – Data Structures

## Problem Statement
In a busy parking area, manually assigning parking slots and managing vehicles waiting for a slot can become confusing. This project models a small parking facility and automates slot allocation, vehicle lookup, and waiting-list processing.

## Objectives
1. Simulate vehicle entry and exit in a 12-slot parking lot.
2. Use a HashMap to find a parked vehicle's slot.
3. Use a HashSet to track occupied slots and prevent duplicate slot allocation.
4. Use a custom FIFO Queue backed by an ArrayList and a front index to manage vehicles waiting for parking.
5. Display the data structure state through a browser-based interface.

## Working Methodology

### Vehicle Entry
- Validate the registration number and vehicle details.
- Check whether the vehicle is already parked or waiting.
- Find the first slot not present in the occupied-slot set.
- Store the vehicle-to-parking record in the HashMap and mark the slot occupied in the HashSet.
- If no slot is free, add the vehicle to the waiting queue.

### Vehicle Exit
- Look up the vehicle in the HashMap.
- Remove its parking record and remove its slot number from the HashSet.
- If the waiting queue is not empty, dequeue its first vehicle and assign the newly available slot.

### Vehicle Search
- Search parked vehicles using the HashMap.
- Search waiting vehicles by traversing the custom queue.
- Report whether the vehicle is parked, waiting, or not found.

## Data Structures

| Structure | Role |
|---|---|
| HashMap | Vehicle registration number to parking record |
| HashSet | Set of occupied slot numbers |
| Custom Queue | ArrayList plus front index; preserves FIFO order without using Java's built-in Queue |

## Complexity
- HashMap lookup: O(1) average
- HashSet membership/add/remove: O(1) average
- Custom ArrayList queue enqueue: amortized O(1); dequeue: O(1)
- Scan for a free slot: O(n), where n is the number of parking slots
- Search within waiting queue: O(w), where w is the number of waiting vehicles

## Demonstration Scenario
Load 14 sample vehicles into 12 parking spaces. The first 12 vehicles should be parked and the remaining 2 should wait. When vehicle PB10AB1003 exits from Slot 3, the first waiting vehicle should be allocated Slot 3. This demonstrates FIFO behavior.

## Scope
This is a small educational simulation. It does not connect to real parking hardware, payment systems, sensors, cameras, or external services.
