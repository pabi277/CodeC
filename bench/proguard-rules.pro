# Phase 25.1 bench spike — throwaway harness rules.
#
# Sora is kept whole so R8 can never break the C-sora candidate on the one
# device round this APK exists for. CAVEAT for the decision table's APK-size
# row: a real Phase 25.2 integration would ship only the consumer rules
# sora-editor publishes, so the +size measured here is an upper bound.
-keep class io.github.rosemoe.sora.** { *; }
-dontwarn io.github.rosemoe.sora.**

# The bench dispatches synthesized KeyEvents/MotionEvents into the view tree
# and reads android.view.FrameMetrics — no reflection into app classes, so
# nothing else needs keeping; the Compose consumer rules ship with the libs.
