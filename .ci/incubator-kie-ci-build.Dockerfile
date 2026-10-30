#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# CI builder image for incubator-kie (Drools) Jenkins jobs.
# Same content as apache-nodes/Dockerfile.kogito-ci-build (incubator-kie-kogito-pipelines, main-latest),
# moved here so it can be published per stream by .ci/jenkins/Jenkinsfile.ci-image-build, e.g.
#   docker.io/apache/incubator-kie-kogito-ci-build:10.3.x-latest
#
# Docker-in-Docker: the daemon is started by ENTRYPOINT entrypoint.sh (-> start-docker.sh),
# so the container must run privileged (--privileged --group-add docker).
#
# Keep SDKMAN_MAVEN in sync with <version.org.apache.maven> in kie-parent/pom.xml
# (enforced by maven-enforcer-plugin, rule requireMavenVersion).
FROM cruizba/ubuntu-dind:jammy-26.1.4

SHELL ["/bin/bash", "-c"]

ARG SDKMAN_JAVA="17.0.11-tem"
ARG SDKMAN_MAVEN="3.9.16"
ARG PYTHON_MAJOR_VERSION="3"
ARG PYTHON_MAJOR_MINOR_VERSION="3.11"
ARG DOCKER_VERSION="24.0.7"

# set locale to C.UTF-8
ENV LANG='C.UTF-8'

# set TZ to America/New_York
ENV TZ='America/New_York'

RUN apt update && apt upgrade -y && apt install -y \
# skdman deps (BEGIN)
git \
findutils \
tar \
gzip \
unzip \
zip \
# skdman deps (END)
# python3 (BEGIN)
python${PYTHON_MAJOR_MINOR_VERSION} \
python${PYTHON_MAJOR_MINOR_VERSION}-dev \
python${PYTHON_MAJOR_VERSION}-pip \
python${PYTHON_MAJOR_VERSION}-gssapi \
krb5-multidev \
# python3 (END)
# system (BEGIN)
netcat \
libvshadow-utils \
sudo \
wget \
software-properties-common \
# system (END)
# drools (BEGIN)
fontconfig \
# drools (END)
# kogito python integration (BEGIN)
libglvnd0 \
# kogito python integration (END)
# Cypress dependencies install (BEGIN)
xvfb \
libgtk2.0-dev \
libgtk-3-dev \
libnotify-dev \
libnss3-tools \
libxss1 \
libasound2 \
# Cypress dependencies install (END)
# kogito-images (BEGIN)
jq \
skopeo \
subversion \
# kogito-images (END)
&& apt clean

RUN groupadd -g 910 nonrootuser && useradd -u 910 -g 910 -s /bin/bash -m nonrootuser && \
  echo "nonrootuser ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

# Docker
RUN wget -O docker.tgz https://download.docker.com/linux/static/stable/x86_64/docker-${DOCKER_VERSION}.tgz  && \
  tar --extract --file docker.tgz --strip-components 1 --directory /usr/local/bin/ && \
  rm -rf docker.tgz && \
  groupadd docker && \
  usermod -aG docker nonrootuser && \
  newgrp docker

USER nonrootuser

# Install pip of given version (BEGIN)
RUN curl -sS https://bootstrap.pypa.io/get-pip.py | python${PYTHON_MAJOR_MINOR_VERSION}
# Install pip of given version (END)

RUN sudo update-alternatives --install /usr/local/bin/python python $(which python${PYTHON_MAJOR_MINOR_VERSION}) 1 && \
  sudo update-alternatives --install /usr/local/bin/python3 python3 $(which python${PYTHON_MAJOR_MINOR_VERSION}) 1 && \
  sudo update-alternatives --install /usr/local/bin/pip pip ~/.local/bin/pip${PYTHON_MAJOR_MINOR_VERSION} -1 && \
  sudo update-alternatives --install /usr/local/bin/pip${PYTHON_MAJOR_VERSION} pip${PYTHON_MAJOR_VERSION} ~/.local/bin/pip${PYTHON_MAJOR_MINOR_VERSION} -1 && \
  sudo update-alternatives --install /usr/local/bin/pip${PYTHON_MAJOR_MINOR_VERSION} pip${PYTHON_MAJOR_MINOR_VERSION} ~/.local/bin/pip${PYTHON_MAJOR_MINOR_VERSION} -1

RUN curl -s "https://get.sdkman.io" | bash && \
  source "$HOME/.sdkman/bin/sdkman-init.sh" && \
  sdk install java ${SDKMAN_JAVA} && \
  sudo update-alternatives --install /usr/local/bin/java java $(which java) 1 && \
  sdk install maven ${SDKMAN_MAVEN} && \
  sudo update-alternatives --install /usr/local/bin/mvn mvn $(which mvn) 1 && \
  sdk flush
  
RUN curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.5/install.sh | bash && \
  export NVM_DIR="$HOME/.nvm" && \
  [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && \
  [ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion" && \
  source ~/.bashrc && nvm install "v16.20.0" && \
  sudo update-alternatives --install /usr/local/bin/node node $(which node) 1 && \
  sudo update-alternatives --install /usr/local/bin/npm npm $(which npm) 1
  
RUN wget https://go.dev/dl/go1.21.6.linux-amd64.tar.gz -P /tmp && \
  sudo mkdir /opt/golang && \
  sudo tar -C /opt/golang -xzf /tmp/go*.tar.gz && \
  sudo chown -R nonrootuser:nonrootuser /opt/golang/go && \
  sudo chmod -R 755 /opt/golang/go && \
  echo 'export GOPATH=${HOME}/go' | sudo tee /etc/profile.d/go.sh && \
  echo 'export PATH=${PATH}:/opt/golang/go/bin:${GOPATH}/bin' | sudo tee -a /etc/profile.d/go.sh && \
  echo "source /etc/profile.d/go.sh" >> $HOME/.bashrc && \
  rm -rf /tmp/go* && \
  sudo update-alternatives --install /usr/local/bin/go go /opt/golang/go/bin/go 1 && \
  go install golang.org/x/tools/cmd/goimports@latest && \
  sudo update-alternatives --install /usr/local/bin/goimports goimports ~/go/bin/goimports 1

# Install hub CLI (used for GitHub api operations)
RUN wget https://github.com/mislav/hub/releases/download/v2.14.2/hub-linux-amd64-2.14.2.tgz -O /tmp/hub.tgz && \
  (if ([ ! -d /opt/hub ]);then sudo mkdir /opt/hub;else rm -rf /opt/hub/*; fi;) && \
  sudo tar -C /opt/hub -xzf /tmp/hub.tgz --strip-components=1 && \
  sudo chown -R nonrootuser:nonrootuser /opt/hub/bin/hub && \
  sudo chmod -R 755 /opt/hub/bin/hub && \
  rm -rf /tmp/hub.tgz && \
  sudo update-alternatives --install /usr/local/bin/hub hub /opt/hub/bin/hub 1

# Cekit
RUN pip${PYTHON_MAJOR_MINOR_VERSION} install cekit==4.11.0 behave lxml docker docker-squash elementPath pyyaml ruamel.yaml python-dateutil Jinja2 pykwalify colorlog click && \
  sudo update-alternatives --install /usr/local/bin/cekit cekit ~/.local/bin/cekit 1 && \
  sudo update-alternatives --install /usr/local/bin/docker-squash docker-squash ~/.local/bin/docker-squash 1
RUN wget https://github.com/openshift/source-to-image/releases/download/v1.3.8/source-to-image-v1.3.8-980ca195-linux-amd64.tar.gz -P /tmp && \
  tmp_dir=$(mktemp -d) && \
  tar -C ${tmp_dir} -xzvf /tmp/source-to-image-v1.3.8-980ca195-linux-amd64.tar.gz && \
  ls ${tmp_dir} && \
  sudo mv ${tmp_dir}/s2i /usr/local/bin/ && \
  rm -rf ${tmp_dir} /tmp/source-to-image/*

# gh cli
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | sudo dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
  && sudo chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
  && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" | sudo tee /etc/apt/sources.list.d/github-cli.list > /dev/null \
  && sudo apt update \
  && sudo apt install gh -y

# Install kubectl
RUN wget https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl -P /tmp && \
  chmod +x /tmp/kubectl && \
  sudo mv /tmp/kubectl /usr/local/bin/kubectl

# Install oc client
RUN wget https://mirror.openshift.com/pub/openshift-v4/clients/ocp/latest-4.13/openshift-client-linux.tar.gz -P /tmp && \
  tmp_dir=$(mktemp -d) && \
  tar -C ${tmp_dir} -xvf /tmp/openshift-client-linux.tar.gz && \
  sudo mv ${tmp_dir}/oc /usr/local/bin && \
  rm -rf ${tmp_dir} /tmp/openshift-client*

# Install ansible
RUN sudo ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ | sudo tee /etc/timezone && \
    sudo add-apt-repository --yes --update ppa:ansible/ansible && \
    sudo apt update && \
    sudo apt install -y ansible

# Convenience script to account for using 'alternatives' in some places
RUN sudo bash -c 'echo -e "#!/bin/bash\nupdate-alternatives \"\$@\"" > /usr/local/bin/alternatives' \
  && sudo chmod +x /usr/local/bin/alternatives

ENV HOME="/home/nonrootuser"

ENV JAVA_HOME="${HOME}/.sdkman/candidates/java/current/"
ENV MAVEN_HOME="${HOME}/.sdkman/candidates/maven/current/"
ENV MAVEN_OPTS="-Xms1024m -Xmx12g"
ENV NODE_HOME="${HOME}/.nvm/versions/node/v16.20.0"
ENV NODE_OPTIONS="--max_old_space_size=4096"
ENV GOPATH="${HOME}/go"
ENV GOROOT="/opt/golang/go"
ENV PATH="${PATH}:${GOROOT}/bin:${GOPATH}/bin"

ENV CONTAINER_ENGINE="docker"
ENV CONTAINER_ENGINE_TLS_OPTIONS=""

WORKDIR /project/directory

USER root
COPY .ci/ci-image/start-docker.sh .ci/ci-image/wait-for-docker.sh .ci/ci-image/wait-for-process.sh .ci/ci-image/entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/start-docker.sh \
	/usr/local/bin/entrypoint.sh \
  /usr/local/bin/wait-for-docker.sh \
  /usr/local/bin/wait-for-process.sh
USER nonrootuser

RUN echo "source wait-for-docker.sh" >> ~/.bashrc

# Use bash as default instead of dash (BEGIN)
RUN sudo bash -c "echo \"dash dash/sh boolean false\" | debconf-set-selections"
RUN sudo bash -c "DEBIAN_FRONTEND=noninteractive dpkg-reconfigure dash"
# Use bash as default instead of dash (END)

# Disable quarkus usage analytics (BEGIN)
RUN mkdir ~/.redhat && (echo '{"disabled":true}' > ~/.redhat/io.quarkus.analytics.localconfig)
# Disable quarkus usage analytics (END)

ENTRYPOINT ["entrypoint.sh"]
CMD ["bash"]
