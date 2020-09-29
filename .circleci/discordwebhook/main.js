const axios = require("axios").default

const branch = process.argv[2]
const version = process.argv[3]
const build = process.argv[4]
const compareUrl = process.argv[5]

axios
  .get(compareUrl)
  .then(res => {
    let success = true
    let description = ""

    description += "**Branch:** " + branch
    description += "\n**Status:** " + (success ? "success" : "failure")

    let changes = "\n\n**Changes:**"
    let hasChanges = false
    for (let i in res.data.commits) {
      let commit = res.data.commits[i]

      changes += "\n- [`" + commit.sha.substring(0, 7) + "`](https://github.com/MineGame159/meteor-client/commit/" + commit.sha + ") *" + commit.commit.message + "*"
      hasChanges = true
    }
    if (hasChanges) description += changes

    if (success) {
      description += "\n\n**Download:** [meteor-client-" + version + "-" + build + "](https://" + build + "-256699023-gh.circle-artifacts.com/0/build/libs/meteor-client-" + version + "-" + build + ".jar)"
    }

    axios.post("https://discordapp.com/api/webhooks/760506437348229151/PDbacrTK-dHeYtRVb4YPj-bzb_bj4Bs_Q6Bga8iA4SLXFeKS6prj13uqQs0St5FLKWHF", {
      username: "Dev Builds",
      avatar_url: "https://meteorclient.com/icon.png",
      embeds: [
        {
          title: "meteor client v" + version + " build #" + build,
          description: description,
          url: "https://meteorclient.com",
          color: success ? 3066993 : 15158332,
          thumbnail: {
            url: "https://meteorclient.com/icon.png"
          }
        }
      ]
    })
  })