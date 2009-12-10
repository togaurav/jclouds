/**
 *
 * Copyright (C) 2009 Cloud Conscious, LLC. <info@cloudconscious.com>
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 */
package org.jclouds.vcloud.terremark.compute;

import java.net.InetAddress;
import java.util.SortedSet;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.Image;
import org.jclouds.compute.Profile;
import org.jclouds.compute.Server;
import org.jclouds.compute.domain.CreateServerResponse;
import org.jclouds.compute.domain.LoginType;
import org.jclouds.compute.domain.internal.CreateServerResponseImpl;
import org.jclouds.domain.Credentials;
import org.jclouds.logging.Logger;
import org.jclouds.rest.domain.NamedResource;
import org.jclouds.vcloud.VCloudMediaType;
import org.jclouds.vcloud.domain.VApp;
import org.jclouds.vcloud.terremark.TerremarkVCloudClient;
import org.jclouds.vcloud.terremark.domain.InternetService;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.inject.internal.ImmutableSet;

/**
 * @author Adrian Cole
 */
@Singleton
public class TerremarkVCloudComputeService implements ComputeService {
   @Resource
   protected Logger logger = Logger.NULL;
   private final TerremarkVCloudComputeClient computeClient;
   private final TerremarkVCloudClient tmClient;

   @Inject
   public TerremarkVCloudComputeService(TerremarkVCloudClient tmClient,
            TerremarkVCloudComputeClient computeClient) {
      this.tmClient = tmClient;
      this.computeClient = computeClient;

   }

   @Override
   public CreateServerResponse createServer(String name, Profile profile, Image image) {
      String id = computeClient.start(name, 1, 512, image);
      VApp vApp = tmClient.getVApp(id);
      Iterable<InetAddress> privateAddresses = vApp.getNetworkToAddresses().values();

      InetAddress sshIp = null;
      InternetService is;
      for (int port : new int[] { 22, 80, 8080 }) {
         is = tmClient.addInternetService(id + "-" + port, "TCP", port);
         tmClient.addNode(is.getId(), Iterables.getLast(privateAddresses), id + "-" + port, port);
         if (port == 22) {
            is.getPublicIpAddress().getAddress();
         }
      }
      return new CreateServerResponseImpl(id, vApp.getName(), ImmutableSet.<InetAddress> of(sshIp),
               privateAddresses, 22, LoginType.SSH, new Credentials("vcloud", "p4ssw0rd"));
   }

   @Override
   public Server getServerById(String id) {
      return new TerremarkVCloudServer(computeClient, tmClient.getVApp(id));
   }

   public SortedSet<InternetService> getInternetServicesByName(final String name) {
      return Sets.newTreeSet(Iterables.filter(tmClient.getAllInternetServices(),
               new Predicate<InternetService>() {
                  @Override
                  public boolean apply(InternetService input) {
                     return input.getName().equalsIgnoreCase(name);
                  }
               }));
   }

   @Override
   public SortedSet<Server> getServerByName(final String name) {
      return Sets.newTreeSet(Iterables.filter(listServers(), new Predicate<Server>() {
         @Override
         public boolean apply(Server input) {
            return input.getName().equalsIgnoreCase(name);
         }
      }));
   }

   @Override
   public SortedSet<Server> listServers() {
      SortedSet<Server> servers = Sets.newTreeSet();
      for (NamedResource resource : tmClient.getDefaultVDC().getResourceEntities().values()) {
         if (resource.getType().equals(VCloudMediaType.VAPP_XML)) {
            servers.add(getServerById(resource.getId()));
         }
      }
      return servers;
   }
}