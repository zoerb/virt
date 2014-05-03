function proxy() {
  var args = Array.prototype.slice.call(arguments);
  var nsFunc = args[0].replace(/-/g, "_").replace(/\//g, ".");

  eval(nsFunc).apply(null, args.slice(1));
}
