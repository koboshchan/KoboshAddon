# Minecraft 26.3 rendering audit

All addon-owned rendering uses Wurst 26.3 or Mojang rendering abstractions.
No OpenGL state changes or native OpenGL entrypoints are needed.

World overlays in PlayerESP, AdvancedItemESP, BedFinder, BlockLogger, Grid,
OreSim, TrialSpawnerESP, SpawnerPlayerESP, Teleport, FastExcavator,
FastTunneller, Filler and NBTViewer use Wurst RenderUtils. Path and digging
overlays delegate to Wurst's PathFinder/BlockBreakingProgress. These use the
pinned Wurst RenderType/RenderPipeline and WurstBufferSource implementation,
which submits through Mojang's render device rather than an OpenGL backend.

GUI overlays in HackList, NBTViewer, FastExcavator, FastTunneller and Filler
use Mojang GuiGraphicsExtractor. CordTags edits EntityRenderState name text
during extraction and leaves nametag submission to the vanilla renderer.
The Wurst logo mixin changes visibility, not rendering state.

The existing 26.3 port already uses these APIs; this audit deliberately keeps
feature behavior and settings intact instead of rewriting working draw paths.
The Gradle check task now rejects OpenGL package references and GlStateManager
in both Java source and compiled addon classes.

CI uses Java 25, Gradle 9.8.2 and the committed Wurst submodule gitlink. It must
not switch the dependency to a same-named moving branch. The addon and Wurst
must be built with matching Minecraft versions.

Static checks and compilation are not an in-game Vulkan test. No game was
launched during this audit.
