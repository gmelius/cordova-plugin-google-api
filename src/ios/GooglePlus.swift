import GoogleSignIn

@objc(GooglePlus)
class GooglePlus: CDVPlugin, GIDSignInDelegate {
    var commandCallback: String?
    
    
    /**** SignIn SDK ****/
        
    /*
    func application(_ app: UIApplication, open url: URL, options: [UIApplicationOpenURLOptionsKey : Any] = [:]) -> Bool {
        return GIDSignIn.sharedInstance().handle(url as URL?,
                                                 sourceApplication: options[UIApplicationOpenURLOptionsKey.sourceApplication] as? String,
                                                 annotation: options[UIApplicationOpenURLOptionsKey.annotation])
    }
    
    
    func application(application: UIApplication,
                     openURL url: URL, sourceApplication: String?, annotation: Any?) -> Bool {
        var options: [String: AnyObject] = [UIApplicationOpenURLOptionsSourceApplicationKey: sourceApplication,
                                            UIApplicationOpenURLOptionsAnnotationKey: annotation]
        return GIDSignIn.sharedInstance().handleURL(url,
                                                    sourceApplication: sourceApplication,
                                                    annotation: annotation)
    }
    */
    
    func sign(_ signIn: GIDSignIn!, didSignInFor user: GIDGoogleUser!,
              withError error: Error!) {
        if let error = error {
            print("\(error.localizedDescription)")
        } else {
            print(user)
            do {
                let hasImage: Bool = user.profile.hasImage
                
                if let message = try String(
                    data: JSONSerialization.data(
                        withJSONObject: [
                          "idToken": user.authentication.idToken,
                          "fullName": user.profile.name,
                          "givenName": user.profile.givenName,
                          "familyName": user.profile.familyName,
                          "email": user.profile.email/* ,
                          "imageUrl": hasImage ? user.profile.imageURLWithDimension(60) : nil */
                        ],
                        options: []
                    ),
                    encoding: String.Encoding.utf8
                    ) {
                    self.send(message)
                }
                else {
                    self.sendError("Serializing result failed.")
                }
                
            } catch let error {
                self.sendError(error.localizedDescription)
            }
            
            
        }
    }
    
    func sign(_ signIn: GIDSignIn!, didDisconnectWith user: GIDGoogleUser!,
              withError error: Error!) {
        self.sendError("Something is wrong !")
    }
    
    /**** END ****/
    
    
    
    
    @objc(login:)
    func login (command: CDVInvokedUrlCommand) {
        self.getGIDSignInObject(command).signIn()
        
        /* GIDSignIn.sharedInstance().handle(
         URL(options["url"] as! String),
         sourceApplication: options[UIApplicationOpenURLOptionsKey.sourceApplication] as? String,
         annotation: options[UIApplicationOpenURLOptionsKey.annotation]
         )*/
        
    }
    
    func getGIDSignInObject (_ command: CDVInvokedUrlCommand) -> GIDSignIn! {
        self.commandCallback = command.callbackId

        let options: [String: Any] = command.arguments.first as! [String: Any]
        print(options)
        
        let reversedClientId: String! = self.getReversedClientId()
        
        if (reversedClientId == nil) {
            self.sendError("Could not find REVERSED_CLIENT_ID url scheme in app .plist")
            return nil
        }
        
        let scopesString: String = options["scopes"] as! String
        let serverClientId: String = options["webClientId"] as! String
        let offline: Bool = options["offline"] as! Bool
        
        // Initialize sign-in
        let signInObj: GIDSignIn = GIDSignIn.sharedInstance()
        signInObj.clientID = self.reverseUrlScheme(reversedClientId)
        signInObj.delegate = self
        
        if (offline) {
            signInObj.serverClientID = serverClientId
        }
        
        signInObj.scopes = scopesString.split(separator: ".").map(String.init)
        
        return signInObj
    }
    
    func reverseUrlScheme (_ reversedClientId: String) -> String {
        var originalArray: Array = reversedClientId.split(separator: ".").map(String.init)
        originalArray.reverse()
        let reversedString: String = originalArray.joined(separator: ".")
        
        return reversedString
    }
    // Get the REVERSED_CLIENT_ID
    func getReversedClientId () -> String! {
        if let urlTypes: [Any] = Bundle.main.infoDictionary!["CFBundleURLTypes"] as? [Any] {
            for value in urlTypes {
                let dict: [String: Any] = value as! [String: Any]
                let urlName: String = dict["CFBundleURLName"] as! String
                if (urlName == "REVERSED_CLIENT_ID") {
                    if let urlSchemes: [String] = dict["CFBundleURLSchemes"] as? [String] {
                        return urlSchemes[0]
                    }
                }
            }
        }
        return nil
    }
    
    // Send result
    func send (_ message: String, _ status: CDVCommandStatus = CDVCommandStatus_OK) {
        if let callbackId = self.commandCallback {
            self.commandCallback = nil
            let pluginResult = CDVPluginResult(
                status: status,
                messageAs: message
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    // Send error
    func sendError (_ message: String) {
        self.send(message, CDVCommandStatus_ERROR)
    }
}