"use strict";

/**
 * Various utility functions used throughout Mocha's codebase.
 *
 * @module utils
 */

/** Module dependencies. */

var path = require("path");
var util = require("util");
var he = require("he");

var assign = (exports.assign = require("object.assign").getPolyfill());

/**
 * Inherit the prototype methods from one constructor into another.
 *
 * @param {function} ctor - Constructor function which needs to inherit the prototype.
 * @param {function} superCtor - Constructor function to inherit prototype from.
 * @throws {TypeError} If either constructor is null, or if super constructor lacks a prototype.
 */
exports.inherits = util.inherits;

/**
 * Escape special characters in the given string of html.
 *
 * @private
 * @param {string} html
 * @returns {string}
 */
exports.escape = function (html) {
  return he.encode(String(html), { useNamedReferences: false });
};

/**
 * Test if the given obj is type of string.
 *
 * @private
 * @param {Object} obj
 * @returns {boolean}
 */
exports.isString = function (obj) {
  return typeof obj === "string";
};

/**
 * Compute a slug from the given `str`.
 *
 * @private
 * @param {string} str
 * @returns {string}
 */
exports.slug = function (str) {
  return str
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^-\w]/g, "")
    .replace(/-{2,}/g, "-");
};

/**
 * Strip the function definition from `str`, and re-indent for pre whitespace.
 *
 * @param {string} str
 * @returns {string}
 */
exports.clean = function (str) {
  str = str
    .replace(/\r\n?|[\n\u2028\u2029]/g, "\n")
    .replace(/^\uFEFF/, "")
    // (traditional)->  space/name     parameters    body     (lambda)-> parameters       body   multi-statement/single          keep body content
    .replace(
      /^function(?:\s*|\s+[^(]*)\([^)]*\)\s*\{((?:.|\n)*?)\s*\}$|^\([^)]*\)\s*=>\s*(?:\{((?:.|\n)*?)\s*\}|((?:.|\n)*))$/,
      "$1$2$3",
    );

  var spaces = str.match(/^\n?( *)/)[1].length;
  var tabs = str.match(/^\n?(\t*)/)[1].length;
  var re = new RegExp("^\n?" + (tabs ? "\t" : " ") + "{" + (tabs || spaces) + "}", "gm");

  str = str.replace(re, "");

  return str.trim();
};

/**
 * If a value could have properties, and has none, this function is called, which returns a string
 * representation of the empty value.
 *
 * Functions w/ no properties return `'[Function]'` Arrays w/ length === 0 return `'[]'` Objects w/
 * no properties return `'{}'` All else: return result of `value.toString()`
 *
 * @private
 * @param {any} value The value to inspect.
 * @param {string} typeHint The type of the value
 * @returns {string}
 */
function emptyRepresentation(value, typeHint) {
  switch (typeHint) {
    case "function":
      return "[Function]";
    case "object":
      return "{}";
    case "array":
      return "[]";
    default:
      return value.toString();
  }
}

/**
 * Takes some variable and asks `Object.prototype.toString()` what it thinks it is.
 *
 * @private
 * @example
 *   type({}) // 'object'
 *   type([]) // 'array'
 *   type(1) // 'number'
 *   type(false) // 'boolean'
 *   type(Infinity) // 'number'
 *   type(null) // 'null'
 *   type(new Date()) // 'date'
 *   type(/foo/) // 'regexp'
 *   type('type') // 'string'
 *   type(global) // 'global'
 *   type(new String('foo') // 'object'
 *
 * @param {any} value The value to test.
 * @returns {string} Computed type
 * @see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/toString
 */
var type = (exports.type = function type(value) {
  if (value === undefined) {
    return "undefined";
  }

  if (value === null) {
    return "null";
  }

  if (Buffer.isBuffer(value)) {
    return "buffer";
  }

  return Object.prototype.toString
    .call(value)
    .replace(/^\[.+\s(.+?)]$/, "$1")
    .toLowerCase();
});

/**
 * Stringify `value`. Different behavior depending on type of value:
 *
 * - If `value` is undefined or null, return `'[undefined]'` or `'[null]'`, respectively.
 * - If `value` is not an object, function or array, return result of `value.toString()` wrapped in
 *   double-quotes.
 * - If `value` is an _empty_ object, function, or array, return result of function
 *   {@link emptyRepresentation}.
 * - If `value` has properties, call {@link exports.canonicalize} on it, then return result of
 *   JSON.stringify().
 *
 * @private
 * @param {any} value
 * @returns {string}
 * @see exports.type
 */
exports.stringify = function (value) {
  var typeHint = type(value);

  if (!~["object", "array", "function"].indexOf(typeHint)) {
    if (typeHint === "buffer") {
      var json = Buffer.prototype.toJSON.call(value);
      // Based on the toJSON result
      return jsonStringify(json.data && json.type ? json.data : json, 2).replace(/,(\n|$)/g, "$1");
    }

    // IE7/IE8 has a bizarre String constructor; needs to be coerced
    // into an array and back to obj.
    if (typeHint === "string" && typeof value === "object") {
      value = value.split("").reduce(function (acc, char, idx) {
        acc[idx] = char;
        return acc;
      }, {});
      typeHint = "object";
    } else {
      return jsonStringify(value);
    }
  }

  for (var prop in value) {
    if (Object.prototype.hasOwnProperty.call(value, prop)) {
      return jsonStringify(exports.canonicalize(value, null, typeHint), 2).replace(
        /,(\n|$)/g,
        "$1",
      );
    }
  }

  return emptyRepresentation(value, typeHint);
};

/**
 * Like JSON.stringify but more sense.
 *
 * @private
 * @param {Object} object
 * @param {number} [spaces]
 * @param {number} [depth]
 * @returns {any}
 */
function jsonStringify(object, spaces, depth) {
  if (typeof spaces === "undefined") {
    // Primitive types
    return _stringify(object);
  }

  depth = depth || 1;
  var space = spaces * depth;
  var str = Array.isArray(object) ? "[" : "{";
  var end = Array.isArray(object) ? "]" : "}";
  var length = typeof object.length === "number" ? object.length : Object.keys(object).length;
  // `.repeat()` polyfill
  function repeat(s, n) {
    return new Array(n).join(s);
  }

  function _stringify(val) {
    switch (type(val)) {
      case "null":
      case "undefined":
        val = "[" + val + "]";
        break;
      case "array":
      case "object":
        val = jsonStringify(val, spaces, depth + 1);
        break;
      case "boolean":
      case "regexp":
      case "symbol":
      case "number":
        val =
          val === 0 && 1 / val === -Infinity // `-0`
            ? "-0"
            : val.toString();
        break;
      case "date":
        var sDate = isNaN(val.getTime()) ? val.toString() : val.toISOString();
        val = "[Date: " + sDate + "]";
        break;
      case "buffer":
        var json = val.toJSON();
        // Based on the toJSON result
        json = json.data && json.type ? json.data : json;
        val = "[Buffer: " + jsonStringify(json, 2, depth + 1) + "]";
        break;
      default:
        val = val === "[Function]" || val === "[Circular]" ? val : JSON.stringify(val); // String
    }

    return val;
  }

  for (var i in object) {
    if (!Object.prototype.hasOwnProperty.call(object, i)) {
      continue; // Not my business
    }

    --length;
    str +=
      "\n " +
      repeat(" ", space) +
      (Array.isArray(object) ? "" : '"' + i + '": ') + // Key
      _stringify(object[i]) + // Value
      (length ? "," : ""); // Comma
  }

  return (
    str +
    // [], {}
    (str.length !== 1 ? "\n" + repeat(" ", --space) + end : end)
  );
}

/**
 * Return a new Thing that has the keys in sorted order. Recursive.
 *
 * If the Thing...
 *
 * - Has already been seen, return string `'[Circular]'`
 * - Is `undefined`, return string `'[undefined]'`
 * - Is `null`, return value `null`
 * - Is some other primitive, return the value
 * - Is not a primitive or an `Array`, `Object`, or `Function`, return the value of the Thing's
 *   `toString()` method
 * - Is a non-empty `Array`, `Object`, or `Function`, return the result of calling this function
 *   again.
 * - Is an empty `Array`, `Object`, or `Function`, return the result of calling
 *   `emptyRepresentation()`
 *
 * @private
 * @param {any} value Thing to inspect. May or may not have properties.
 * @param {Array} [stack=[]] Stack of seen values. Default is `[]`
 * @param {string} [typeHint] Type hint
 * @returns {Object | Array | Function | string | undefined}
 * @see {@link exports.stringify}
 */
exports.canonicalize = function canonicalize(value, stack, typeHint) {
  var canonicalizedObj;
  var prop;
  typeHint = typeHint || type(value);
  function withStack(value, fn) {
    stack.push(value);
    fn();
    stack.pop();
  }

  stack = stack || [];

  if (stack.indexOf(value) !== -1) {
    return "[Circular]";
  }

  switch (typeHint) {
    case "undefined":
    case "buffer":
    case "null":
      canonicalizedObj = value;
      break;
    case "array":
      withStack(value, function () {
        canonicalizedObj = value.map(function (item) {
          return exports.canonicalize(item, stack);
        });
      });
      break;
    case "function":
      /* eslint-disable-next-line no-unused-vars */
      for (prop in value) {
        canonicalizedObj = {};
        break;
      }

      if (!canonicalizedObj) {
        canonicalizedObj = emptyRepresentation(value, typeHint);
        break;
      }

    /* Falls through */
    case "object":
      canonicalizedObj = canonicalizedObj || {};
      withStack(value, function () {
        Object.keys(value)
          .sort()
          .forEach(function (key) {
            canonicalizedObj[key] = exports.canonicalize(value[key], stack);
          });
      });
      break;
    case "date":
    case "number":
    case "regexp":
    case "boolean":
    case "symbol":
      canonicalizedObj = value;
      break;
    default:
      canonicalizedObj = String(value);
  }

  return canonicalizedObj;
};

/**
 * Process.emitWarning or a polyfill
 *
 * @ignore
 * @see https://nodejs.org/api/process.html#process_process_emitwarning_warning_options
 */
function emitWarning(msg, type) {
  if (process.emitWarning) {
    process.emitWarning(msg, type);
  } else {
    process.nextTick(function () {
      console.warn(type + ": " + msg);
    });
  }
}

/**
 * Show a deprecation warning. Each distinct message is only displayed once. Ignores empty messages.
 *
 * @private
 * @param {string} [msg] - Warning to print
 */
exports.deprecate = function deprecate(msg) {
  msg = String(msg);
  if (msg && !deprecate.cache[msg]) {
    deprecate.cache[msg] = true;
    emitWarning(msg, "DeprecationWarning");
  }
};

exports.deprecate.cache = {};

/**
 * Show a generic warning. Ignores empty messages.
 *
 * @private
 * @param {string} [msg] - Warning to print
 */
exports.warn = function warn(msg) {
  if (msg) {
    emitWarning(msg);
  }
};

/**
 * When invoking this function you get a filter function that get the Error.stack as an input, and
 * return a prettify output. (i.e: strip Mocha and internal node functions from stack trace).
 *
 * @returns {Function}
 * @summary
 * This Filter based on `mocha-clean` module.(see: `github.com/rstacruz/mocha-clean`)
 */
exports.stackTraceFilter = function () {
  // TODO: Replace with `process.browser`
  var is = typeof document === "undefined" ? { node: true } : { browser: true };
  var slash = path.sep;
  var cwd;
  if (is.node) {
    cwd = exports.cwd() + slash;
  } else {
    cwd = (typeof location === "undefined" ? window.location : location).href.replace(
      /\/[^/]*$/,
      "/",
    );
    slash = "/";
  }

  function isMochaInternal(line) {
    return (
      ~line.indexOf("node_modules" + slash + "mocha" + slash) ||
      ~line.indexOf(slash + "mocha.js") ||
      ~line.indexOf(slash + "mocha.min.js")
    );
  }

  function isNodeInternal(line) {
    return (
      ~line.indexOf("(timers.js:") ||
      ~line.indexOf("(events.js:") ||
      ~line.indexOf("(node.js:") ||
      ~line.indexOf("(module.js:") ||
      ~line.indexOf("GeneratorFunctionPrototype.next (native)") ||
      false
    );
  }

  return function (stack) {
    stack = stack.split("\n");

    stack = stack.reduce(function (list, line) {
      if (isMochaInternal(line)) {
        return list;
      }

      if (is.node && isNodeInternal(line)) {
        return list;
      }

      // Clean up cwd(absolute)
      if (/:\d+:\d+\)?$/.test(line)) {
        line = line.replace("(" + cwd, "(");
      }

      list.push(line);
      return list;
    }, []);

    return stack.join("\n");
  };
};

/**
 * Crude, but effective.
 *
 * @param {any} value
 * @returns {boolean} Whether or not `value` is a Promise
 * @public
 */
exports.isPromise = function isPromise(value) {
  return typeof value === "object" && value !== null && typeof value.then === "function";
};

/**
 * Clamps a numeric value to an inclusive range.
 *
 * @param {number} value - Value to be clamped.
 * @param {numer[]} range - Two element array specifying [min, max] range.
 * @returns {number} Clamped value
 */
exports.clamp = function clamp(value, range) {
  return Math.min(Math.max(value, range[0]), range[1]);
};

/**
 * Single quote text by combining with undirectional ASCII quotation marks.
 *
 * Provides a simple means of markup for quoting text to be used in output. Use this to quote names
 * of variables, methods, and packages.
 *
 * <samp>package 'foo' cannot be found</samp>
 *
 * @private
 * @example
 *   sQuote("n"); // => 'n'
 *
 * @param {string} str - Value to be quoted.
 * @returns {string} Quoted value
 */
exports.sQuote = function (str) {
  return "'" + str + "'";
};

/**
 * Double quote text by combining with undirectional ASCII quotation marks.
 *
 * Provides a simple means of markup for quoting text to be used in output. Use this to quote names
 * of datatypes, classes, pathnames, and strings.
 *
 * <samp>argument 'value' must be "string" or "number"</samp>
 *
 * @private
 * @example
 *   dQuote("number"); // => "number"
 *
 * @param {string} str - Value to be quoted.
 * @returns {string} Quoted value
 */
exports.dQuote = function (str) {
  return '"' + str + '"';
};

/**
 * It's a noop.
 *
 * @public
 */
exports.noop = function () {};

/**
 * Creates a map-like object.
 *
 * A "map" is an object with no prototype, for our purposes. In some cases this would be more
 * appropriate than a `Map`, especially if your environment doesn't support it. Recommended for use
 * in Mocha's public APIs.
 *
 * @param {...any} [obj] - Arguments to `Object.assign()`.
 * @returns {Object} An object with no prototype, having `...obj` properties
 * @public
 * @see {@link https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Map|MDN:Map}
 * @see {@link https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/create#Custom_and_Null_objects|MDN:Object.create - Custom objects}
 * @see {@link https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/assign|MDN:Object.assign}
 */
exports.createMap = function () {
  return assign.apply(null, [Object.create(null)].concat(Array.prototype.slice.call(arguments)));
};

/**
 * Creates a read-only map-like object.
 *
 * This differs from {@link module:utils.createMap createMap} only in that the argument must be
 * non-empty, because the result is frozen.
 *
 * @param {...any} [obj] - Arguments to `Object.assign()`.
 * @returns {Object} A frozen object with no prototype, having `...obj` properties
 * @throws {TypeError} If argument is not a non-empty object.
 * @see {@link module:utils.createMap createMap}
 */
exports.defineConstants = function (obj) {
  if (type(obj) !== "object" || !Object.keys(obj).length) {
    throw new TypeError("Invalid argument; expected a non-empty object");
  }

  return Object.freeze(exports.createMap(obj));
};

/**
 * Whether current version of Node support ES modules
 *
 * Versions prior to 10 did not support ES Modules, and version 10 has an old incompatible version
 * of ESM. This function returns whether Node.JS has ES Module supports that is compatible with
 * Mocha's needs, which is version >=12.11.
 *
 * @param {partialSupport} whether The full Node.js ESM support is available (>= 12) or just
 *   something that supports the runtime (>= 10)
 * @returns {Boolean} Whether the current version of Node.JS supports ES Modules in a way that is
 *   compatible with Mocha
 */
exports.supportsEsModules = function (partialSupport) {
  if (!exports.isBrowser() && process.versions && process.versions.node) {
    var versionFields = process.versions.node.split(".");
    var major = Number(versionFields[0]);
    var minor = Number(versionFields[1]);

    if (!partialSupport) {
      return major >= 13 || (major === 12 && minor >= 11);
    }

    return major >= 10;
  }
};

/**
 * Returns current working directory
 *
 * Wrapper around `process.cwd()` for isolation
 *
 * @private
 */
exports.cwd = function cwd() {
  return process.cwd();
};

/**
 * Returns `true` if Mocha is running in a browser. Checks for `process.browser`.
 *
 * @private
 * @returns {boolean}
 */
exports.isBrowser = function isBrowser() {
  return Boolean(process.browser);
};
