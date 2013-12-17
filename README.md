s3-object-streamer
==================

A wrapper around S3Objects that makes streaming and working with S3Objects, and dealing with issues like the underlying HTTPS connection being closed, easier.

Overview
========

S3 is a fantastic service for storing files. But if you want to store large files (say, batch data) which you then want to process in chunks, it's not so great. S3 Objects are retrieved via HTTPS, which will timeout if you take too long to read it.

It would be great if the AWS SDK could hide that from you - but it doesn't. It _does_ give you the ability to start a download from a particular location in the file (using the HTTP Range header), though. And that's where this project comes in.

This class is an extension to the AWS SDK for Java. It provides a utility class that, given a  handle to a S3 Object, will give you an InputStream that will transparently recover from timeouts. This means you can then write code like this:

    S3Object myS3Object = ...
    try(BufferedInputStream myStream = new S3ObjectStreamer(myS3Object)) {
        ... read some data
        ... do something that might take a long time with that data
    } catch (IOException e) {
        ... do what you need to do on an IOException.
    }

And you don't have to worry about server or client-side timeouts.

Caveats
=======

* You _do_ have to worry about the data being changed underneath you. The `S3ObjectStreamer` uses the etag returned on the first request to ensure that subsequent blocks of data are consistent. If the resource has changed, you will get an IOException. Same goes if it's deleted while being processed, obviously
* The S3ObjectStreamer is itself a buffered stream. The default buffer size is 1MB, which you can tune as needed (up to MAX_INT in size). You will obviously need to have enough memory available for the buffer.
* If there are IOExceptions in populating the buffer, the S3ObjectStreamer will simply pass it up - the only error scenario this attempts to solve is the underlying HTTPS timeout! You can configure the underlying S3Client to do things such as transparently retry in the event of a transient failure.

Credits
=====

This work wouldn't be possible without Amazon's Web Services, or their Java SDK.

This project was developed by [Robert Watkins](http://twasink.net) (robertdw@twasink.net), in his own time, in part to address an issue observed whilst working for the [Julius Kruttschnitt Mining Research Centre](http://jkmrc.com.au), on the [IES Project](http://www.crcore.org.au/integrated-extraction-simulator.html) - a project co-sponsored by [CRC-ORE](http://www.crcore.org.au).

This project is copyright 2013 to Robert Watkins.

License
=======

This code is licensed under the MIT License. See the included LICENSE file for details. In general, that means you can do pretty much whatever you want, as long as you keep the license file and don't try to claim your own copyright over it.

Forking of the code, or incorporation into your own code base, is both permitted and strongly encouraged.