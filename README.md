# TorPathSim

**TorPathSim** is a simple simulation tool to experiment with and analyse resilience of Tor network path selection rules
against Entry-Exit Correlation Attacks. Interactions between user-specified models of Defenders (Tor Network Designers)
and Adversaries are tested with the real Tor network's composition of nodes.

It was written as part of a 3rd year project for my Computer Science degree, contributing to my dissertation on
*Practical and Game-Theoretic Analysis of Path Selection to Defend Against
Entry-Exit Correlation in the Tor Network*. Originally written in Java, TorPathSim has subsequently been converted to
Kotlin and updated to address the most pressing issues with the initial implementation - particularly the performance
issues discussed in the Evaluation section of the dissertation.

## Motivation

Anyone may run a node in the Tor network, potentially many nodes. The user's address is necessarily exposed to the node
where they enter the network and their destination is necessarily exposed to the node where their traffic exits the
network. For anonymity to be maintained it is crucial a connection cannot be drawn between these two pieces of
information.

Entry-Exit Correlation is the general technique for drawing that connection, for example by comparing the timing and
volume of traffic. The simplest mitigation to this attack is to make raising the likelihood of controlling both the
Entry and Exit node in a selected path as difficult or costly as possible.

TorPathSim allows you to:

1. **Analyse real Tor Network data** (Consensus documents).
2. **Define Defender Strategies:** Rules for how clients select nodes.
3. **Define Adversary Strategies:** Rules for how an attacker compromises (acquires) their malicious nodes.
4. **Simulate & Measure:** Run thousands of Monte Carlo simulations to determine the probability of circuit compromise.

## Improvements

For posterity (and a discussion of my more recent work) the most impactful subsequent improvements have been:

* **Highly Performant Simulation (~500× Faster):** Uses Kotlin coroutines to simulate significant numbers of clients in
  an efficient and idiomatic way, achieving ~68,000 paths/sec (~1,200× higher core simulation throughput than the
  original single-lock SQLite implementation, completing 50,000 paths in ~1.8 seconds). While the original SQLite
  database aligned pleasingly with my mental model of the problem, it was a poor choice for this use case (though the
  implementation of the choice was also suboptimal) — an in-memory cache with pre-indexed flag views needs orders of
  magnitude less memory and zero disk I/O.
* **Fixed Algorithm & Logic Bugs:** Resolved uncaught flaws in the original implementation, including an inverted
  boundary check, an issue where a node could sometimes be selected twice for a path, and not properly checking that an
  adversary had used as much of a bandwidth budget as possible.
* **Improved Statistical Analysis & Useful Outputs:** Added formatted comparison tables alongside network utility
  and diversity metrics (Effective Load-Balanced Bandwidth, Max Node Load, Gini Coefficient, and Shannon Entropy).
* **Automated Testing:** Unit testing covering consensus parsing, node filtering, weighted
  selection, adversary budgets, client path construction, and CLI converters.

## Installation

### Prerequisites

* **Java 25** (GraalVM Community).
* **Gradle 9**

### Building

```bash
# Build the standalone CLI distribution
./gradlew build

# Compile a native binary via GraalVM
./gradlew nativeCompile
```

## Use

The CLI operates via two main subcommands: `data` and `analyse`.

### 1. Download Consensus Data (`data`)

Downloads the current consensus from a Tor Directory Authority.

```bash
# Syntax
./build/install/TorPathSim/bin/TorPathSim data --downloadSource <AUTHORITY_NAME> --saveLocation <FILE_PATH>

# Example: Download from the 'moria1' authority
./build/install/TorPathSim/bin/TorPathSim data -dl moria1 -sv tor_consensus
```

* **Authorities:** See `configs/directory_authorities.toml`

### 2. Run Analysis (`analyse`)

Simulates clients creating paths based on defined rules.

```bash
# Syntax
./build/install/TorPathSim/bin/TorPathSim analyse --loadData <DATA_FILE> --rules <RULES_FILE> --advstrat <ADVERSARY_FILE> [options]

# Example: Simulate 5000 clients selecting 10 paths each
./build/install/TorPathSim/bin/TorPathSim analyse -ld tor_consensus -r configs/modern_tor_config.txt -a configs/standard_adversary.txt -c 5000 -p 10
```

#### Output

```text
Loading consensus data into memory...
Loaded 9066 records from tor_consensus.
Removed 47 unsuitable nodes.
Simulating 5000 clients using coroutines...

Analysis complete:
Created 5000 clients.
Successfully selected 3 guards and 10 paths per client. (50000 paths in total).

Adversary Control:
Compromised 1810 nodes (20.07%). Total Bandwidth: 39668725 bytes/sec.

1366 (27.32%) clients experienced compromise.
Total 1986 (3.97%) paths compromised.

+-----------+-------+---------------+---------+--------+----------------+
| Group     | Count | Avg Bandwidth | Guard % | Exit % | Guard & Exit % |
+-----------+-------+---------------+---------+--------+----------------+
| Network   | 9019  | 21991.75      | 52.72%  | 29.62% | 14.69%         |
+-----------+-------+---------------+---------+--------+----------------+
| Adversary | 1810  | 21916.42      | 53.04%  | 28.78% | 14.42%         |
+-----------+-------+---------------+---------+--------+----------------+

Path Utility:
Average Path Bandwidth (Naive): 23848.23 bytes/sec
Average Effective Path Bandwidth (Load-balanced): 833.94 bytes/sec
Most Overwhelmed Node Load: 300 connections
Gini Coefficient of Node Load: 0.4496 (0 = perfect equality, 1 = maximum inequality)
Selection Diversity (Shannon Entropy): 12.0963 bits (Score: 92.07%)
```

#### Options

| Option       | Short | Description                                                  | Default |
|:-------------|:------|:-------------------------------------------------------------|:--------|
| `--loadData` | `-ld` | Path to the consensus dataset to load (required)             |         |
| `--rules`    | `-r`  | Path to the defender rules configuration file (required)     |         |
| `--advstrat` | `-a`  | Path to the adversary strategy configuration file (required) |         |
| `--clients`  | `-c`  | Number of clients to simulate                                | `50`    |
| `--paths`    | `-p`  | Number of paths per client                                   | `10`    |

## Configuration Files

### Defender Rules (`-r`, `--rules`)

Defines how a client builds a circuit.

* **order:** The selection order of nodes (e.g. `3 1 2` means firstly select an Exit, then a Guard and finally a Middle
  node).
* **guards:** Number of guards a client pins.
* **pool{1-3}:** Constraints for each position in path.

**Example (Modern Tor Approximation):**

```text
order 3 1 2
guards 3
# Pool 1 (Entry): Must be a Guard, Must NOT be an Exit
pool1 --guard Positive --exit Negative
# Pool 2 (Middle): Must NOT be a Guard, Must NOT be an Exit
pool2 --guard Negative --exit Negative
# Pool 3 (Exit): Must be an Exit
pool3 --exit Positive
```

### Adversary Strategy (`-a`, `--advstrat`)

Defines the resources available to the attacker.

* **selectBy:** `nodes` (count) or `bandwidth` (total capacity).
* **amount:** The budget (integer value or percentage).
* **Pool lines:** Percentage distribution and pool constraints.

**Example (Attacker controls 5% of network bandwidth, prioritizing Guards):**

```text
select --selectBy bandwidth --amount 5%
# 100% of the budget goes to compromising Guard nodes
100 --guard Positive
```

## Licence & Copyright

* **Software:** The TorPathSim source code and associated configuration files are licensed under
  the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).
* **Dissertation:** The dissertation ([*"Practical and Game-Theoretic Analysis of Path Selection to Defend Against Entry-Exit Correlation in the Tor Network"*](docs/Practical%20and%20Game-Theoretic%20Analysis%20of%20Path%20Selection%20to%20Defend%20Against%20Entry-Exit%20Correlation%20in%20the%20Tor%20Network.pdf)) is (c) 2023-2026 Aidan E. S. **All rights reserved.** It is expressly excluded from the AGPL-3.0 licence.