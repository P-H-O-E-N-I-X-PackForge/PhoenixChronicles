<h1 align="center">
  <img src="https://raw.githubusercontent.com/Omicron-Industries/PhoenixChronicles/main/chronicle.png" alt="Phoenix Chronicles" width="280">
</h1>

<p align="center">
  <strong>Create your favorite documentation; thinly disguised as a mod or an interconnected story. 
    It's your choice (or not).</strong>
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/phoenix-chronicles-quests">
    <img alt="CurseForge" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/available/curseforge_vector.svg"></a>
  <a href="https://discord.gg/4jch9Rs2Cq">
    <img alt="Discord" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/social/discord-singular_vector.svg"></a>
  <a href="https://ko-fi.com/phoenixvine">
    <img alt="Ko-fi" height="50" src="https://raw.githubusercontent.com/intergrav/devins-badges/v3/assets/cozy/donate/kofi-singular_vector.svg"></a>
</p>


# Phoenix Chronicles
Chronicles is a simple 1.20.1 questbook mod made to solve the niche and vast issues when making a questbook for your modpack.

It is focused on trying to integrate with the community, provide everything a questbook mod would need, and throw some more into the mix.

It has compat for
- Gregtech:Modern
- Phoenix Guilds
- Phantasia
- PhoenixCore
- FTBTeams
It also depends on PhoenixWiki for the shared theming with the rest of the PhoenixSuite 

Made to be an alternative to **FTBQuests** (or ftbq) but still have its own unique feel and cadence. 
There is a built-in **FTBQuests** importer that can handle importing 
*quests, player progress, chapters, categories, rewards, and tasks*.
Though `filter tasks` will have to be remade since those use a seperate mod not in base **FTBQuests**.

## Wiki link
We have a small in progress wiki for all PhoenixSuite mods, if it is missing any important info or you would like to help,
feel free to ping me on discord by the username of Phoenixvine.

[Wiki](https://omicron-industries.github.io/PhoenixSuite/wiki/) 

# Getting started as a dev and/or packdev.
You will need the minecraft development plugin and the mermaid plugin.
The minecraft formatting colors plugin is also highly suggested. 
Checking the wiki is also very helpful as well as there being some more resources down below.
And ofc last but not least feel free to ask for help in the discord

## Major feature list.
Below is a chart of the major features and how they compare to other mods in Chronicle's niche.
There is also a chart of what we do better or at parity to what others do.
For more information on any of the features, check the wiki (or if that doesn't exist) ping Phoenixvine on discord.

Legend: PR = At Parity, AA = Advantage, PA = Partial.

| Feature                           | Chronicles                                     | FTB Quests                         | Parity / Advantage                     |
|:----------------------------------|:-----------------------------------------------|:-----------------------------------|:---------------------------------------|
| **GregTech Integration**          | Built-in native support for GT recipes & tiers | Requires third-party addon scripts | **Advantage** (Out-of-the-box support) |
| **Quest Editor Gui**              | Lightweight, custom layout rendering           | Heavy, highly configurable GUI     | **Parity** (Different style focus)     |
| **Custom External tasks**         | Custom player capability tracking              | Custom data attachments            | **AA**                                 |
| **Packet Syncing**                | Optimized light payload network packets        | Standard networking pipeline       | **Advantage** (Lower bandwidth)        |
| **Third-Party Mod Compatibility** | Direct Mixin & API bridges                     | Extensive integrations             | **In Progress**                        |


# Roadmap
Below is a loose roadmap of what we plan to do in the future.

```mermaid
flowchart LR
%% Style Classes
classDef done fill:#2e7d32,stroke:#1b5e20,color:#ffffff
classDef active fill:#f57f17,stroke:#e65100,color:#ffffff
classDef planned fill:#1565c0,stroke:#0d47a1,color:#ffffff

    subgraph PackDev ["Packdev Documentation"]
        direction TB
        P1["Quest Creation & Groups - Quests, Chapters, Categories"]
        P2["Quest Layout & Positioning - Resizing, Moving, Auto Arrange"]
        P3["Prerequisites & Dependencies - Gating & Line Styles"]
        P4["Variants & Flags System"]
        P5["Quest Markdown Support"]
        P6["Visual Styling - Node Shapes & Icons"]
        P7["Quest State Logic - Locked / Available / Hidden / Complete"]
        P8["Advanced Quest Mechanics - Linked Quests & Dev Notes"]
        P9["Rewards & Tasks - Types, Choiced & Lootbox"]
        P10["Filter System"]
        P11["Import Tooling - FTBQuests Importer"]
        P12["Automated Localization - Auto Lang & Lang Editor"]
    end

    subgraph Dev ["Developer Documentation"]
        direction TB
        D1["Java API Specifications"]
        D2["KubeJS Integration Plugin"]
        D3["Quest Engine Profiler"]
        D4["Dev Utilities & Tooling - Reset, Delete, Force-Complete"]
        D5["Inventory & Performance Engine - Fingerprint Hash & AE2 Handling"]
    end

    subgraph PackDev_Dev ["Packdev & Dev Documentation"]
        direction TB
        PD1["Theming Systems - Theme Editor, Shaders, Backgrounds"]
        PD2["Dynamic Theming - Conditional Sidebar & Category Themes"]
        PD3["Optional Mod Compatibility - Phantasia, PhoenixCore, Phoenix Domains"]
    end

    subgraph PackDev_Player ["Packdev & Player Documentation"]
        direction TB
        PP1["In-Game Quest Editor"]
        PP2["Popup Editor Window"]
        PP3["Search System"]
        PP4["Settings Page Overview"]
    end

    subgraph Player ["Player-Facing Documentation"]
        direction TB
        PL1["Quest Viewers - Fullscreen vs. Compact View"]
        PL2["UI Panels - Minimap, Stats, & Subgraph"]
    end

    subgraph Future ["Engine & Porting Milestones"]
        direction TB
        M1["In-Game Wiki Engine - Bundled In-Game Docs"]
        M2["Port to Minecraft 1.21.1"]
        M3["Port to GregTech Modern 8.0"]
    end

%% Dependency & Progression Flow
P1 --> P2 --> P3
P1 --> P8
P4 --> P5
P6 --> P7
P9 --> P10
P12 --> M1

    D1 --> D2
    D3 --> D5

    PD1 --> PD2

    PP1 --> PP2

%% Target Milestones Unlocks
P12 --> M1
D5 --> M2
PD3 --> M3

%% Apply Classes
class P1,P2,P3,P4,P5,P6,P7,P8,P9,P10,P11,P12 planned
class D1,D2,D3,D4,D5 planned
class PD1,PD2,PD3 planned
class PP1,PP2,PP3,PP4 planned
class PL1,PL2 planned
class M1,M2,M3 planned
```

# Small snippt of the markdown of quests.

# Credits
- Thanks to FTBQuests for some feature ideas and things to do a bit better.
- Thanks to Omicron Industries (especially PlasmaticVoid) for their help in refining the project.
- Thanks to [Jambon](https://github.com/Jambon123), FyreDrakon, and KaiTheExaminer
for helping test out the mod and giving some suggestions.
- Thanks to [Rose](https://github.com/Lilac-Rose) for adding porting over ftb quest player data. 
"slanderous of ftbquestsl; stupid fucking formatting" Lilac Aria Rose; 2026

# Ai disclosure.
A sizeable portion of the code has been written by ai. Refactoring is currently ongoing.
All text in the mod is currently being taken apart and being rewritten by a real human, and all documentation is written 
by humans.

# Where to go next.
Contributing
See CONTRIBUTING.md. (Note, WIP. Feel free to ask Phoenix questions if anything is unclear)

Architecture Decisions 
See Architecture.md. (Note, WIP. Feel free to ask Phoenix questions if anything is unclear)

Frequently Asked Questions
See FAQ.md (Note, this is not complete. More entries will be added as questions are asked.)

Known Issues
See KNOWN-ISSUES.md (Note, currently empty. Want to be the first?)