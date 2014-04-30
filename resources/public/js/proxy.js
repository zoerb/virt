function proxy() {
  var args = Array.prototype.slice.call(arguments);
  eval(args[0]).apply(null, args.slice(1));
}
