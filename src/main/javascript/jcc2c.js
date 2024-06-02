/*
 *  Copyright 2024 Carlos Machado
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * A script for converting JaCoCo XML coverage reports into Cobertura 
 * XML coverage reports.
 *
 * See: https://github.com/rix0rrr/cover2cover/blob/master/cover2cover.py
 */

var File = Java.type('java.io.File');
var StringWriter = Java.type('java.io.StringWriter');
var System = Java.type('java.lang.System');

var Document = Java.type('org.w3c.dom.Document');
var DocumentBuilder = Java.type('javax.xml.parsers.DocumentBuilder');
var DocumentBuilderFactory = Java.type('javax.xml.parsers.DocumentBuilderFactory');
var DOMSource = Java.type('javax.xml.transform.dom.DOMSource');
var Element = Java.type('org.w3c.dom.Element');
var NodeList = Java.type('org.w3c.dom.NodeList');
var OutputKeys = Java.type('javax.xml.transform.OutputKeys');
var StreamResult = Java.type('javax.xml.transform.stream.StreamResult');
var TransformerFactory = Java.type('javax.xml.transform.TransformerFactory');
var Transformer = Java.type('javax.xml.transform.Transformer');

function nodeToString(node) {
    var transFactory = TransformerFactory.newInstance();
    var transformer = transFactory.newTransformer();
    var buffer = new StringWriter();
    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, 'yes');
    transformer.setOutputProperty(OutputKeys.INDENT, 'yes');
    transformer.setOutputProperty('{http://xml.apache.org/xslt}indent-amount', '2');
    transformer.transform(new DOMSource(node), new StreamResult(buffer));
    var str = buffer.toString();
    return str;
}

function find_lines(j_package, filename) {
    debugger;
    var basename = new File(filename).name;
    var lines = [];
    var sourcefiles = j_package.getElementsByTagName('sourcefile');
    for (var i = 0; i < sourcefiles.length; i++) {
        var sourcefile = sourcefiles.item(i);
        if (basename.equals(sourcefile.getAttribute('name'))) {
            var sourcefile_lines = sourcefile.getElementsByTagName('line');
            for (var j = 0; j < sourcefile_lines.length; j++) {
                var line = sourcefile_lines.item(j);
                lines.push(line);
            }
        }
    }
    return lines;
}

function line_is_after(jm, start_line) {
    var jm_line = jm.getAttribute('line');
    if (!jm_line) {
        jm_line = 0;
    }
    return jm_line > start_line;
}

function method_lines(jmethod, jmethods, jlines) {
    var start_line = jmethod.getAttribute('line');
    if (!start_line) {
        start_line = 0;
    }
    
    var larger = [];
    for each (var jm in jmethods) {
        if (line_is_after(jm, start_line)) {
            var jm_line = jm.getAttribute('line');
            if (!jm_line) {
                jm_line = 0;
            }
            larger.push(jm_line);
        }
    }
    var end_line;
    if (larger.length > 0) {
        end_line = Math.min.apply(Math, larger);
    } else {
        end_line = 99999999;
    }
    var result = [];
    for each (var jline in jlines) {
        if (start_line <= jline.getAttribute('nr') && jline.getAttribute('nr') < end_line) {
            result.push(jline);
        }
    }
    return result;
}

function convert_lines(tree, j_lines, into) {
    var c_lines = tree.createElement('lines');
    for each (var jline in j_lines) {
        var mb = jline.getAttribute('mb');
        var cb = jline.getAttribute('cb');
        var ci = jline.getAttribute('ci');
        var cm_plus_mb = (cb + mb);

        var cline = tree.createElement('line');
        cline.setAttribute('number', jline.getAttribute('nr'));
        cline.setAttribute('hits', ci > 0 ? '1' : '0'); // Probably not true but no way to know from JaCoCo XML file

        if (mb + cb > 0) {
            var percentage = 100.0 * (cb / cm_plus_mb);
            percentage = percentage + '%';
            cline.setAttribute('branch', 'true');
            cline.setAttribute('condition-coverage', percentage + ' (' + cb + '/' + cm_plus_mb + ')');
            
            var conds = tree.createElement('conditions');
            cond = tree.createElement('condition');
            cond.setAttribute('number', '0');
            cond.setAttribute('type', 'jump');
            cond.setAttribute('coverage', percentage);
            conds.appendChild(cond);
            cline.appendChild(conds);
        } else {
            cline.setAttribute('branch', 'false');
        }
        c_lines.appendChild(cline);
    }
    into.appendChild(c_lines);
}

function guess_filename(path_to_class) {
    var re = /([^\$]*)/;
    var m = path_to_class.match(re);
    if (m) {
        return m[1] + '.java';
    }
    return path_to_class + '.java';
}

function add_counters(source, target) {
    target.setAttribute('line-rate',   counter(source, 'LINE'));
    target.setAttribute('branch-rate', counter(source, 'BRANCH'));
    target.setAttribute('complexity', counter(source, 'COMPLEXITY', sum));
}

function fraction(covered, missed) {
    return covered / (covered + missed);
}

function sum(covered, missed) {
    return covered + missed;
}

function counter(source, type, operation) {
    if (!operation) {
        operation = fraction;
    }
    var cs = source.getElementsByTagName('counter');
    var c = null;
    for (var i = 0; i < cs.length; i++) {
        var ct = cs.item(i);
        if (type.equals(ct.getAttribute('type'))) {
            c = ct;
            break;
        }
    }
    if (c) {
        covered = c.getAttribute('covered');
        missed  = c.getAttribute('missed');

        return operation(covered, missed);
    }
    return '0.0';
}

function convert_method(tree, j_method, j_lines) {
    var c_method = tree.createElement('method');
    c_method.setAttribute('name', j_method.getAttribute('name'));
    c_method.setAttribute('signature', j_method.getAttribute('desc'));

    add_counters(j_method, c_method);
    convert_lines(tree, j_lines, c_method);

    return c_method;
}

function convert_class(tree, j_class, j_package) {
    var c_class = tree.createElement('class');
    c_class.setAttribute('name', j_class.getAttribute('name').replaceAll('/', '.'));
    c_class.setAttribute('filename', guess_filename(j_class.getAttribute('name')));
    
    var all_j_lines = find_lines(j_package, c_class.getAttribute('filename'));
    
    var c_methods = tree.createElement('methods');
    var sourceMethods = j_class.getElementsByTagName('method');
    var all_j_methods = [];
    for (var i = 0; i < sourceMethods.length; i++) {
        var sourceMethod = sourceMethods.item(i);
        all_j_methods.push(sourceMethod);
    }
    for each (var j_method in all_j_methods) {
        var j_method_lines = method_lines(j_method, all_j_methods, all_j_lines);
        c_methods.appendChild(convert_method(tree, j_method, j_method_lines));
    }
    c_class.appendChild(c_methods);
    
    add_counters(j_class, c_class);
    convert_lines(tree, all_j_lines, c_class);
    
    return c_class;
}

function convert_package(tree, j_package) {
    var c_package = tree.createElement('package');
    c_package.setAttribute('name', j_package.getAttribute('name').replaceAll('/', '.'));
    
    c_classes = tree.createElement('classes');
    var sourceClasses = j_package.getElementsByTagName('class');
    for (var i = 0; i < sourceClasses.length; i++) {
        var j_class = sourceClasses.item(i);
        c_classes.appendChild(convert_class(tree, j_class, j_package));
    }
    c_package.appendChild(c_classes);
    
    add_counters(j_package, c_package);
    
    return c_package;
}

function convert_root(tree, target, source_roots) {
    var source = tree.documentElement;
    
    var list = source.getElementsByTagName('sessioninfo');
    if (list.length <= 0) {
        System.err.println("[ERROR] Could not find the 'sessioninfo' XML element.");
        return;
    }
    
    var sessioninfo = list.item(0);
    var start = sessioninfo.getAttribute('start');
    target.setAttribute('timestamp', start / 1000);

    var sources = tree.createElement('sources');
    source_roots.forEach(function(value, index, vector) {
        var source = tree.createElement('source');
        source.textContent = value;
        sources.appendChild(source);
    });
    target.appendChild(sources);
    
    var packages = tree.createElement('packages');
    var sourcePackages = source.getElementsByTagName('package');
    for (var i = 0; i < sourcePackages.length; i++) {
        var package = sourcePackages.item(i);
        packages.appendChild(convert_package(tree, package));
    }
    target.appendChild(packages);
    
    add_counters(source, target);
}

function jacoco2cobertura(filename, source_roots) {
    var factory = DocumentBuilderFactory.newInstance();
    factory.validating = false;
    factory.namespaceAware = true;
    factory.setFeature('http://xml.org/sax/features/namespaces', false);
    factory.setFeature('http://xml.org/sax/features/validation', false);
    factory.setFeature('http://apache.org/xml/features/nonvalidating/load-dtd-grammar', false);
    factory.setFeature('http://apache.org/xml/features/nonvalidating/load-external-dtd', false);

    var builder = factory.newDocumentBuilder();

    var tree;
    if ('-'.equals(filename)) {
        tree = builder.parse(System.in);
    } else {
        var file = new File(filename);
        tree = builder.parse(file);
    }
    
    var into = tree.createElement('coverage');
    convert_root(tree, into, source_roots);
    print('<?xml version="1.0" ?>');
    var content = nodeToString(into);
    print(content);
}

function main(args) {
    if (args.length < 1) {
        System.err.print('Usage: jjs jcc2c.js FILENAME [SOURCE_ROOTS...]');
        exit(1);
    }

    var filename = args[0];
    var source_roots;
    if (args.length >= 2) {
        source_roots = args.slice(1, args.length);
    } else {
        source_roots = ['.'];
    }

    jacoco2cobertura(filename, source_roots);
}

main(arguments);
