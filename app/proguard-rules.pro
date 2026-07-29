# Nothing here reaches app code by reflection or from JavaScript, so the default
# optimized ruleset is enough.
#
# If you ever add a @JavascriptInterface class you must keep it explicitly, or R8
# will rename the methods the page calls. Better plan: do not add one.
