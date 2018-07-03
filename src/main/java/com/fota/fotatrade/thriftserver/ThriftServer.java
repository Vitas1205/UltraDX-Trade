// Copyright(c) 2018 The fortuna developers
// This file is part of the fortuna.
//
// fortuna is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// fortuna is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the fortuna. If not, see <http://www.gnu.org/licenses/>.

package com.fota.fotatrade.thriftserver;

import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadedSelectorServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TTransportException;
import org.apache.thrift.transport.TTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Create on 19/06/2018
 * @author JASON.TAO
 */
@Component
public class ThriftServer implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThriftServer.class);

    @Value("${server.thrift.port}")
    private int serverPort;

    @Override
    public void run() {
        startServer();
    }

    public void startServer() {
        LOGGER.info("Starting Thrift Server......!!!");

        try {

            TMultiplexedProcessor multiplexedProcessor = new TMultiplexedProcessor();
            //
//            TProcessor userProcessor = new UserService.Processor<UserService.Iface>(new UserServiceImpl());

            TNonblockingServerSocket serverTransport = new TNonblockingServerSocket(serverPort);
            LOGGER.info("server port " + serverPort);
            TTransportFactory transportFactory = new TFramedTransport.Factory();

            TBinaryProtocol.Factory factory = new TBinaryProtocol.Factory();

            TThreadedSelectorServer.Args tArgs = new TThreadedSelectorServer.Args(serverTransport);
            //客户端协议要一致
            tArgs.protocolFactory(factory);
            tArgs.transportFactory(transportFactory);
            tArgs.processor(multiplexedProcessor);

            // 线程池服务模型，使用标准的阻塞式IO，预先创建一组线程处理请求。
            TServer server = new TThreadedSelectorServer(tArgs);

            server.serve();

        } catch (TTransportException e) {
            LOGGER.error("Starting Thrift Server......Error!!!", e);
        }
    }

}
