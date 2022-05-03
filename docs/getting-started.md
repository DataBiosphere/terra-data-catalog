# Getting Started

These instructions assume you use MacOS, and that you are on the internal Broad
network or the VPN. If the VPN is not installed, follow the instructions
[at this link](https://broad.io/vpn).

## 1. Install Homebrew

[Homebrew](https://brew.sh/) is a [package manager](https://en.wikipedia.org/wiki/Package_manager)
which enables the installation of software using a single, convenient command
line interface. To automatically install development tools necessary for the
team, a [Brewfile](https://github.com/Homebrew/homebrew-bundle) is used:

```
/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
curl -LO https://raw.githubusercontent.com/DataBiosphere/terra-data-catalog/main/docs/Brewfile
brew bundle --no-lock install
```

Running the `brew` command above will install the following tools:

1. [Git](https://git-scm.com/) is a version control tool for tracking changes in
projects and code.
2. [git-secrets](https://github.com/awslabs/git-secrets) prevents developers
from committing passwords and secrets to git.
3. [jq](https://stedolan.github.io/jq/) is a command line JSON processing tool.
4. [Docker](https://www.docker.com/) is a tool to deliver software in packages
called containers. Docker for MacOS also includes [Kubernetes](https://kubernetes.io/),
which deploys groups of containers together in clusters.
5. [Vault](https://www.vaultproject.io/) is an encrypted database used to store
many of the team's secrets such as keys and passwords.
6. [Google Cloud SDK](https://cloud.google.com/sdk) is a command-line interface
to Google Cloud services. Once it is installed, you'll need to allow auth access
and configure Docker to connect to the appropriate Google Cloud endpoint when
necessary, which is done with the configuration below.
7. [IntelliJ IDEA](https://www.jetbrains.com/idea/) is an integrated development
environment (IDE) for Java. There are two versions available: **Ultimate** (paid)
and **Community** (open-source). We recommend the Ultimate Edition to Broad
employees for its database navigation capabilities. Alternatively, the Community
Edition has all the features needed for development, and this version can be
installed by switching `intellij-idea` with `intellij-idea-ce` in the Brewfile.

Unfortunately, some manual configuration is also necessary:

```
# launch docker desktop - this installs docker in /usr/local/bin
open -a docker

# configure google-cloud-sdk
gcloud auth login
gcloud auth application-default login
gcloud auth configure-docker

# ensure that git-secrets patterns are installed
git clone https://github.com/broadinstitute/dsp-appsec-gitsecrets-client.git
./dsp-appsec-gitsecrets-client/gitsecrets.sh
```

## 2. Get Set Up with GitHub and Vault

DevOps has [a document that'll get you connected to our GitHub organization and Vault server](https://docs.google.com/document/d/11pZE-GqeZFeSOG0UpGg_xyTDQpgBRfr0MLxpxvvQgEw/edit#).

Note that you just installed the `vault` CLI above, so you won't need to download it like those docs tell you to.

## 3. Request Required Access

Ensure that you have access to the required team resources. If you encounter a
permission error, it is likely because you are missing appropriate access.

- DataBiosphere: Join the `#github` Slack channel, click the lightning bolt in
the channel header, and select `Join DataBiosphere`.  Once you've been granted
access to DataBiosphere, ask a team member to add your GitHub user to the
[jadeteam](https://github.com/orgs/DataBiosphere/teams/jadeteam) group. This
will give you access to our repositories.
- Google Groups: Ask a team member for access to Google Groups including
`jade-internal` and `dsde-engineering`.

## 4. Handle GitHub notifications

To avoid being overwhelmed with notifications, [add your Broad email address](https://github.com/settings/emails),
[route the notifications](https://github.com/settings/notifications) to that
email, and [unfollow projects](https://github.com/watching) that are not
relevant to your team.

## 5. Create Terra Accounts

The Data Catalog and [Terra](https://terra.bio/) use [Sam](https://github.com/broadinstitute/sam)
to abtract identity and access management. To gain access to these services,
first create a non-Broad email address through Gmail. This email address will
specifically be used for development purposes in our non-prod environments.
Next, to register as a new user, click the `Sign in with Google` button in each
of the environments with the newly created email address and follow the prompts:

- [Dev](https://bvdp-saturn-dev.appspot.com/)
- [Alpha](https://bvdp-saturn-alpha.appspot.com/)
- [Staging](https://bvdp-saturn-staging.appspot.com/)

For [production](https://app.terra.bio/), you will need to register using a
`firecloud.org` email. In order to get an account, you must become suitable,
which requires following [these steps](https://docs.google.com/document/d/1DRftlTe-9Q4H-R0jxanVojvyNn1IzbdIOhNKiIj9IpI/edit?usp=sharing).

Ask a member of the team to add you to the admins group for each of these
environments.

## 6. Install Java 17

Java 17 is required to run the Terra Data Catalog. The latest release can be
found on the [Adoptium releases](https://adoptium.net/temurin/releases/) page.
Ensure you install the latest JDK 17 package ending in `.pkg`.

## 7. Install Postgres 12

[Postgres](https://www.postgresql.org/) is an advanced open-source database.
**Postgres.app** is used to manage a local installation of Postgres. The latest
release can be found on the [GitHub releases](https://github.com/PostgresApp/PostgresApp/releases)
page. For compatibility, make sure to select a version which supports all the
older versions of Postgres including 10. After launching the application,
create a new version 12 database as follows:

1. Click the sidebar icon (bottom left-hand corner) and then click the plus sign
2. Name the new server, making sure to select version **12**, and then
**Initialize** it
3. Add `/Applications/Postgres.app/Contents/Versions/latest/bin` to your path
(there are multiple ways to achieve this)

## 8. Code Checkout

> It may be useful to create a folder for Broad projects in your home directory.

Download the team's projects:

```
git clone git@github.com:DataBiosphere/terra-data-catalog
git clone git@github.com:broadinstitute/terraform-ap-deployments
git clone git@github.com:broadinstitute/terra-helmfile
```
